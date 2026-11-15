-- Habits (owner request 2026-11-12, brought forward from MVP v2): a named daily habit and the local days it was done.
-- habits is a root table (user_id); habit_checks is owned through its habit. A check row = "done that day"; unticking
-- removes the row (a tick is a toggle, not history). Forward-only.
CREATE TABLE habits (
    id          TEXT PRIMARY KEY NOT NULL CHECK (length(id) = 36),
    user_id     TEXT NOT NULL REFERENCES users (id),
    title       TEXT NOT NULL CHECK (length(trim(title)) > 0),
    archived_at TEXT CHECK (archived_at IS NULL OR archived_at GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]T[0-9][0-9]:[0-9][0-9]:[0-9][0-9].[0-9][0-9][0-9]Z'),
    created_at  TEXT NOT NULL CHECK (created_at GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]T[0-9][0-9]:[0-9][0-9]:[0-9][0-9].[0-9][0-9][0-9]Z'),
    updated_at  TEXT NOT NULL CHECK (updated_at GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]T[0-9][0-9]:[0-9][0-9]:[0-9][0-9].[0-9][0-9][0-9]Z')
);
CREATE INDEX idx_habits_user ON habits (user_id, archived_at, created_at);

CREATE TABLE habit_checks (
    habit_id   TEXT NOT NULL REFERENCES habits (id),
    local_date TEXT NOT NULL CHECK (local_date GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]' AND date(local_date) IS local_date),
    created_at TEXT NOT NULL CHECK (created_at GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]T[0-9][0-9]:[0-9][0-9]:[0-9][0-9].[0-9][0-9][0-9]Z'),
    PRIMARY KEY (habit_id, local_date)
);
