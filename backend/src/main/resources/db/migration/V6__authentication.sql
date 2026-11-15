-- Authentication and per-user data ownership.
-- Forward-only and data-preserving: existing rows keep user_id NULL until the first account is registered, which then
-- claims them (application code, same transaction as the registration). Child tables (key_results, check-ins,
-- milestones, goal_history, daily_plan_items) are owned through their parent row and get no column of their own.

CREATE TABLE users (
    id            TEXT PRIMARY KEY NOT NULL CHECK (length(id) = 36),
    -- normalized (trimmed, lower-case) by the application; unique per account
    email         TEXT NOT NULL UNIQUE CHECK (length(email) BETWEEN 3 AND 254 AND email = lower(trim(email))),
    password_hash TEXT NOT NULL CHECK (length(password_hash) > 0),
    display_name  TEXT,
    created_at    TEXT NOT NULL CHECK (created_at GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]T[0-9][0-9]:[0-9][0-9]:[0-9][0-9].[0-9][0-9][0-9]Z'),
    updated_at    TEXT NOT NULL CHECK (updated_at GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]T[0-9][0-9]:[0-9][0-9]:[0-9][0-9].[0-9][0-9][0-9]Z')
);

-- Opaque refresh tokens: only a SHA-256 hash is stored. Rotation chains share a family; reuse of a rotated token
-- revokes the whole family (OAuth 2.0 Security BCP, RFC 9700 §4.14).
CREATE TABLE refresh_tokens (
    id             TEXT PRIMARY KEY NOT NULL CHECK (length(id) = 36),
    user_id        TEXT NOT NULL REFERENCES users (id),
    family_id      TEXT NOT NULL CHECK (length(family_id) = 36),
    token_hash     TEXT NOT NULL UNIQUE CHECK (length(token_hash) = 64),
    expires_at     TEXT NOT NULL CHECK (expires_at GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]T[0-9][0-9]:[0-9][0-9]:[0-9][0-9].[0-9][0-9][0-9]Z'),
    created_at     TEXT NOT NULL CHECK (created_at GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]T[0-9][0-9]:[0-9][0-9]:[0-9][0-9].[0-9][0-9][0-9]Z'),
    revoked_at     TEXT CHECK (revoked_at IS NULL OR revoked_at GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]T[0-9][0-9]:[0-9][0-9]:[0-9][0-9].[0-9][0-9][0-9]Z'),
    revoked_reason TEXT CHECK (revoked_reason IS NULL OR revoked_reason IN ('rotated', 'logout', 'reuse_detected')),
    CHECK ((revoked_at IS NULL) = (revoked_reason IS NULL))
);
CREATE INDEX idx_refresh_tokens_family ON refresh_tokens (family_id);
CREATE INDEX idx_refresh_tokens_user ON refresh_tokens (user_id);

-- HMAC key for signing access tokens, generated once by the application (never shipped, never logged).
CREATE TABLE signing_keys (
    id         TEXT PRIMARY KEY NOT NULL CHECK (length(id) = 36),
    algorithm  TEXT NOT NULL CHECK (algorithm = 'HS256'),
    secret     TEXT NOT NULL CHECK (length(secret) >= 43),
    created_at TEXT NOT NULL CHECK (created_at GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]T[0-9][0-9]:[0-9][0-9]:[0-9][0-9].[0-9][0-9][0-9]Z')
);

-- Owners of the root entities.
ALTER TABLE goals ADD COLUMN user_id TEXT REFERENCES users (id);
ALTER TABLE tasks ADD COLUMN user_id TEXT REFERENCES users (id);
ALTER TABLE focus_items ADD COLUMN user_id TEXT REFERENCES users (id);
ALTER TABLE inbox_items ADD COLUMN user_id TEXT REFERENCES users (id);
CREATE INDEX idx_goals_user ON goals (user_id);
CREATE INDEX idx_tasks_user ON tasks (user_id);
CREATE INDEX idx_focus_items_user ON focus_items (user_id);
CREATE INDEX idx_inbox_items_user ON inbox_items (user_id);

-- daily_plans: one plan per user and date (was: one per date) -> rebuild with its child table, keeping every row.
CREATE TABLE daily_plans_v6 (
    id         TEXT PRIMARY KEY NOT NULL CHECK (length(id) = 36),
    user_id    TEXT REFERENCES users (id),
    local_date TEXT NOT NULL CHECK (local_date GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]' AND date(local_date) IS local_date),
    created_at TEXT NOT NULL CHECK (created_at GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]T[0-9][0-9]:[0-9][0-9]:[0-9][0-9].[0-9][0-9][0-9]Z'),
    updated_at TEXT NOT NULL CHECK (updated_at GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]T[0-9][0-9]:[0-9][0-9]:[0-9][0-9].[0-9][0-9][0-9]Z')
);
INSERT INTO daily_plans_v6 (id, user_id, local_date, created_at, updated_at)
SELECT id, NULL, local_date, created_at, updated_at FROM daily_plans;

CREATE TABLE daily_plan_items_v6 (
    id         TEXT PRIMARY KEY NOT NULL CHECK (length(id) = 36),
    plan_id    TEXT NOT NULL REFERENCES daily_plans_v6 (id),
    task_id    TEXT NOT NULL REFERENCES tasks (id),
    position   INTEGER NOT NULL CHECK (typeof(position) = 'integer' AND position >= 0),
    created_at TEXT NOT NULL CHECK (created_at GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]T[0-9][0-9]:[0-9][0-9]:[0-9][0-9].[0-9][0-9][0-9]Z'),
    CONSTRAINT uq_daily_plan_items_plan_task UNIQUE (plan_id, task_id)
);
INSERT INTO daily_plan_items_v6 (id, plan_id, task_id, position, created_at)
SELECT id, plan_id, task_id, position, created_at FROM daily_plan_items;

DROP TABLE daily_plan_items;
DROP TABLE daily_plans;
-- RENAME also rewrites the foreign key of daily_plan_items_v6 to point at daily_plans.
ALTER TABLE daily_plans_v6 RENAME TO daily_plans;
ALTER TABLE daily_plan_items_v6 RENAME TO daily_plan_items;
CREATE INDEX idx_daily_plan_items_task_id ON daily_plan_items (task_id);
-- NULL user_id (legacy, unclaimed) rows are distinct for UNIQUE; legacy dates were already unique.
CREATE UNIQUE INDEX uq_daily_plans_user_date ON daily_plans (user_id, local_date);
