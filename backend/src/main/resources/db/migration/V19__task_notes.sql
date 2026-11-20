-- Task notes are owned through their task. Permanent deletion removes them with the task.
CREATE TABLE task_notes (
    id         TEXT PRIMARY KEY NOT NULL CHECK (length(id) = 36),
    task_id    TEXT NOT NULL REFERENCES tasks (id),
    body       TEXT NOT NULL CHECK (length(trim(body)) BETWEEN 1 AND 4000),
    created_at TEXT NOT NULL CHECK (created_at GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]T[0-9][0-9]:[0-9][0-9]:[0-9][0-9].[0-9][0-9][0-9]Z'),
    updated_at TEXT NOT NULL CHECK (updated_at GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]T[0-9][0-9]:[0-9][0-9]:[0-9][0-9].[0-9][0-9][0-9]Z')
);
CREATE INDEX idx_task_notes_task ON task_notes (task_id, created_at, id);
