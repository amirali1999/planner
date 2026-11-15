-- Actual daily activity log (owner request 2026-11-14, brought forward from MVP v2).
-- This records what the user actually did in a local time interval; it is not a plan, timer or task status.
CREATE TABLE activity_logs (
    id           TEXT PRIMARY KEY NOT NULL CHECK (length(id) = 36),
    user_id      TEXT NOT NULL REFERENCES users (id),
    local_date   TEXT NOT NULL CHECK (local_date GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]' AND date(local_date) IS local_date),
    title        TEXT NOT NULL CHECK (length(trim(title)) > 0),
    category     TEXT NOT NULL CHECK (category IN ('work', 'study', 'rest', 'personal')),
    start_minute INTEGER NOT NULL CHECK (typeof(start_minute) = 'integer' AND start_minute >= 0 AND start_minute < 1440 AND start_minute % 5 = 0),
    end_minute   INTEGER NOT NULL CHECK (typeof(end_minute) = 'integer' AND end_minute > 0 AND end_minute <= 1440 AND end_minute % 5 = 0),
    created_at   TEXT NOT NULL CHECK (created_at GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]T[0-9][0-9]:[0-9][0-9]:[0-9][0-9].[0-9][0-9][0-9]Z'),
    updated_at   TEXT NOT NULL CHECK (updated_at GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]T[0-9][0-9]:[0-9][0-9]:[0-9][0-9].[0-9][0-9][0-9]Z'),
    CHECK (end_minute > start_minute)
);
CREATE INDEX idx_activity_logs_user_date ON activity_logs (user_id, local_date, start_minute, end_minute);
