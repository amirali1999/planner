-- Weekly time-box (owner request 2026-11-12, brought forward from MVP v2): a block of planned time on one local day,
-- either linked to one of the user's tasks or with its own title. Root table → user_id. Minutes from local midnight,
-- 5-minute steps; blocks of one day never overlap (checked by the service). Forward-only.
CREATE TABLE time_blocks (
    id           TEXT PRIMARY KEY NOT NULL CHECK (length(id) = 36),
    user_id      TEXT NOT NULL REFERENCES users (id),
    local_date   TEXT NOT NULL CHECK (local_date GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]' AND date(local_date) IS local_date),
    start_minute INTEGER NOT NULL CHECK (typeof(start_minute) = 'integer' AND start_minute >= 0 AND start_minute % 5 = 0),
    end_minute   INTEGER NOT NULL CHECK (typeof(end_minute) = 'integer' AND end_minute <= 1440 AND end_minute % 5 = 0),
    title        TEXT CHECK (title IS NULL OR length(trim(title)) > 0),
    task_id      TEXT REFERENCES tasks (id),
    color        TEXT NOT NULL DEFAULT 'teal' CHECK (color IN ('teal', 'blue', 'purple', 'green', 'olive', 'amber', 'rose', 'slate')),
    created_at   TEXT NOT NULL CHECK (created_at GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]T[0-9][0-9]:[0-9][0-9]:[0-9][0-9].[0-9][0-9][0-9]Z'),
    updated_at   TEXT NOT NULL CHECK (updated_at GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]T[0-9][0-9]:[0-9][0-9]:[0-9][0-9].[0-9][0-9][0-9]Z'),
    CHECK (end_minute > start_minute),
    CHECK (task_id IS NOT NULL OR title IS NOT NULL)
);
CREATE INDEX idx_time_blocks_user_date ON time_blocks (user_id, local_date, start_minute);
CREATE INDEX idx_time_blocks_task_id ON time_blocks (task_id);
