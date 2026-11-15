-- Personal Life Planner v2.0 — Demo 0 schema (docs/DATA_MODEL.md).
-- Forward-only: never edit this file after it has been applied; add V2__... instead.
--
-- Storage conventions (enforced by CHECK constraints below):
--   id          TEXT UUID, 36 chars
--   date-only   TEXT 'YYYY-MM-DD' Gregorian; date(x) IS x rejects impossible dates such as 2026-02-30
--   timestamp   TEXT UTC 'YYYY-MM-DDTHH:MM:SS.sssZ' (fixed width, so text order == time order)
--   decimal     TEXT canonical plain decimal string (see DecimalCodec), never REAL
-- No ON DELETE CASCADE: history is preserved, archival uses archived_at.
-- Enumerations are constrained only where docs/DOMAIN_RULES.md defines the vocabulary.

CREATE TABLE goals (
    id             TEXT PRIMARY KEY NOT NULL CHECK (length(id) = 36),
    title          TEXT NOT NULL CHECK (length(trim(title)) > 0),
    description    TEXT,
    period_type    TEXT NOT NULL CHECK (period_type IN ('annual', 'quarterly', 'monthly', 'weekly')),
    period_start   TEXT NOT NULL CHECK (period_start GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]' AND date(period_start) IS period_start),
    period_end     TEXT NOT NULL CHECK (period_end GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]' AND date(period_end) IS period_end),
    deadline       TEXT CHECK (deadline IS NULL OR (deadline GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]' AND date(deadline) IS deadline)),
    status         TEXT NOT NULL DEFAULT 'not_started' CHECK (status IN ('not_started', 'doing', 'done', 'on_hold', 'dropped')),
    parent_goal_id TEXT REFERENCES goals (id) CHECK (parent_goal_id IS NULL OR parent_goal_id <> id),
    archived_at    TEXT CHECK (archived_at IS NULL OR archived_at GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]T[0-9][0-9]:[0-9][0-9]:[0-9][0-9].[0-9][0-9][0-9]Z'),
    created_at     TEXT NOT NULL CHECK (created_at GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]T[0-9][0-9]:[0-9][0-9]:[0-9][0-9].[0-9][0-9][0-9]Z'),
    updated_at     TEXT NOT NULL CHECK (updated_at GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]T[0-9][0-9]:[0-9][0-9]:[0-9][0-9].[0-9][0-9][0-9]Z'),
    CHECK (period_end >= period_start)
);
CREATE INDEX idx_goals_parent_goal_id ON goals (parent_goal_id);
CREATE INDEX idx_goals_period_status ON goals (period_type, status);

CREATE TABLE key_results (
    id          TEXT PRIMARY KEY NOT NULL CHECK (length(id) = 36),
    goal_id     TEXT NOT NULL REFERENCES goals (id),
    name        TEXT NOT NULL CHECK (length(trim(name)) > 0),
    unit        TEXT,
    baseline    TEXT NOT NULL CHECK (typeof(baseline) = 'text' AND baseline GLOB '*[0-9]' AND baseline NOT GLOB '*[^0-9.-]*' AND baseline NOT GLOB '?*-*' AND baseline NOT GLOB '*.*.*'),
    target      TEXT NOT NULL CHECK (typeof(target) = 'text' AND target GLOB '*[0-9]' AND target NOT GLOB '*[^0-9.-]*' AND target NOT GLOB '?*-*' AND target NOT GLOB '*.*.*'),
    weight      TEXT NOT NULL DEFAULT '1' CHECK (typeof(weight) = 'text' AND weight GLOB '*[0-9]' AND weight NOT GLOB '*[^0-9.]*' AND weight NOT GLOB '*.*.*' AND CAST(weight AS REAL) > 0),
    archived_at TEXT CHECK (archived_at IS NULL OR archived_at GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]T[0-9][0-9]:[0-9][0-9]:[0-9][0-9].[0-9][0-9][0-9]Z'),
    created_at  TEXT NOT NULL CHECK (created_at GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]T[0-9][0-9]:[0-9][0-9]:[0-9][0-9].[0-9][0-9][0-9]Z'),
    updated_at  TEXT NOT NULL CHECK (updated_at GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]T[0-9][0-9]:[0-9][0-9]:[0-9][0-9].[0-9][0-9][0-9]Z'),
    CHECK (CAST(baseline AS REAL) <> CAST(target AS REAL))
);
CREATE INDEX idx_key_results_goal_id ON key_results (goal_id);

CREATE TABLE key_result_checkins (
    id            TEXT PRIMARY KEY NOT NULL CHECK (length(id) = 36),
    key_result_id TEXT NOT NULL REFERENCES key_results (id),
    value         TEXT NOT NULL CHECK (typeof(value) = 'text' AND value GLOB '*[0-9]' AND value NOT GLOB '*[^0-9.-]*' AND value NOT GLOB '?*-*' AND value NOT GLOB '*.*.*'),
    measured_at   TEXT NOT NULL CHECK (measured_at GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]T[0-9][0-9]:[0-9][0-9]:[0-9][0-9].[0-9][0-9][0-9]Z'),
    note          TEXT,
    created_at    TEXT NOT NULL CHECK (created_at GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]T[0-9][0-9]:[0-9][0-9]:[0-9][0-9].[0-9][0-9][0-9]Z')
);
-- Supports "current = latest by (measured_at, created_at, id)".
CREATE INDEX idx_kr_checkins_kr_measured ON key_result_checkins (key_result_id, measured_at, created_at, id);

-- Check-ins are append-only: corrections are new rows with a note.
CREATE TRIGGER trg_kr_checkins_no_update BEFORE UPDATE ON key_result_checkins
BEGIN
    SELECT RAISE(ABORT, 'key_result_checkins is append-only');
END;
CREATE TRIGGER trg_kr_checkins_no_delete BEFORE DELETE ON key_result_checkins
BEGIN
    SELECT RAISE(ABORT, 'key_result_checkins is append-only');
END;

-- Anticipatory schema (DATA_MODEL option a): no API/UI until MVP v0; exists so tasks.focus_item_id has a real FK.
-- kind/status vocabularies are not yet specified, so only non-empty is enforced here.
CREATE TABLE focus_items (
    id          TEXT PRIMARY KEY NOT NULL CHECK (length(id) = 36),
    title       TEXT NOT NULL CHECK (length(trim(title)) > 0),
    kind        TEXT NOT NULL CHECK (length(trim(kind)) > 0),
    description TEXT,
    goal_id     TEXT REFERENCES goals (id),
    status      TEXT NOT NULL CHECK (length(trim(status)) > 0),
    archived_at TEXT CHECK (archived_at IS NULL OR archived_at GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]T[0-9][0-9]:[0-9][0-9]:[0-9][0-9].[0-9][0-9][0-9]Z'),
    created_at  TEXT NOT NULL CHECK (created_at GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]T[0-9][0-9]:[0-9][0-9]:[0-9][0-9].[0-9][0-9][0-9]Z'),
    updated_at  TEXT NOT NULL CHECK (updated_at GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]T[0-9][0-9]:[0-9][0-9]:[0-9][0-9].[0-9][0-9][0-9]Z')
);
CREATE INDEX idx_focus_items_goal_id ON focus_items (goal_id);

-- priority vocabulary is not yet specified; validated in the application layer.
CREATE TABLE tasks (
    id                TEXT PRIMARY KEY NOT NULL CHECK (length(id) = 36),
    title             TEXT NOT NULL CHECK (length(trim(title)) > 0),
    description       TEXT,
    goal_id           TEXT REFERENCES goals (id),
    focus_item_id     TEXT REFERENCES focus_items (id),
    status            TEXT NOT NULL DEFAULT 'not_started' CHECK (status IN ('not_started', 'doing', 'done')),
    priority          TEXT,
    estimated_minutes INTEGER CHECK (estimated_minutes IS NULL OR (typeof(estimated_minutes) = 'integer' AND estimated_minutes >= 0)),
    due_date          TEXT CHECK (due_date IS NULL OR (due_date GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]' AND date(due_date) IS due_date)),
    blocked_reason    TEXT,
    archived_at       TEXT CHECK (archived_at IS NULL OR archived_at GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]T[0-9][0-9]:[0-9][0-9]:[0-9][0-9].[0-9][0-9][0-9]Z'),
    created_at        TEXT NOT NULL CHECK (created_at GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]T[0-9][0-9]:[0-9][0-9]:[0-9][0-9].[0-9][0-9][0-9]Z'),
    updated_at        TEXT NOT NULL CHECK (updated_at GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]T[0-9][0-9]:[0-9][0-9]:[0-9][0-9].[0-9][0-9][0-9]Z')
);
CREATE INDEX idx_tasks_goal_id ON tasks (goal_id);
CREATE INDEX idx_tasks_focus_item_id ON tasks (focus_item_id);
CREATE INDEX idx_tasks_status ON tasks (status);

CREATE TABLE daily_plans (
    id         TEXT PRIMARY KEY NOT NULL CHECK (length(id) = 36),
    local_date TEXT NOT NULL UNIQUE CHECK (local_date GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]' AND date(local_date) IS local_date),
    created_at TEXT NOT NULL CHECK (created_at GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]T[0-9][0-9]:[0-9][0-9]:[0-9][0-9].[0-9][0-9][0-9]Z'),
    updated_at TEXT NOT NULL CHECK (updated_at GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]T[0-9][0-9]:[0-9][0-9]:[0-9][0-9].[0-9][0-9][0-9]Z')
);

CREATE TABLE daily_plan_items (
    id         TEXT PRIMARY KEY NOT NULL CHECK (length(id) = 36),
    plan_id    TEXT NOT NULL REFERENCES daily_plans (id),
    task_id    TEXT NOT NULL REFERENCES tasks (id),
    position   INTEGER NOT NULL CHECK (typeof(position) = 'integer' AND position >= 0),
    created_at TEXT NOT NULL CHECK (created_at GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]T[0-9][0-9]:[0-9][0-9]:[0-9][0-9].[0-9][0-9][0-9]Z'),
    CONSTRAINT uq_daily_plan_items_plan_task UNIQUE (plan_id, task_id)
);
CREATE INDEX idx_daily_plan_items_task_id ON daily_plan_items (task_id);
