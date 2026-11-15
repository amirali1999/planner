-- M-01: Goal milestones (docs/DATA_MODEL.md). A milestone is a delivery checkpoint, NOT a Key Result:
-- completing it never changes Outcome or Execution. Completed and archived milestones are kept (no hard delete).
CREATE TABLE milestones (
    id           TEXT PRIMARY KEY NOT NULL CHECK (length(id) = 36),
    goal_id      TEXT NOT NULL REFERENCES goals (id),
    title        TEXT NOT NULL CHECK (length(trim(title)) > 0),
    description  TEXT,
    due_date     TEXT CHECK (due_date IS NULL OR (due_date GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]' AND date(due_date) IS due_date)),
    completed_at TEXT CHECK (completed_at IS NULL OR completed_at GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]T[0-9][0-9]:[0-9][0-9]:[0-9][0-9].[0-9][0-9][0-9]Z'),
    archived_at  TEXT CHECK (archived_at IS NULL OR archived_at GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]T[0-9][0-9]:[0-9][0-9]:[0-9][0-9].[0-9][0-9][0-9]Z'),
    created_at   TEXT NOT NULL CHECK (created_at GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]T[0-9][0-9]:[0-9][0-9]:[0-9][0-9].[0-9][0-9][0-9]Z'),
    updated_at   TEXT NOT NULL CHECK (updated_at GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]T[0-9][0-9]:[0-9][0-9]:[0-9][0-9].[0-9][0-9][0-9]Z')
);
CREATE INDEX idx_milestones_goal_due ON milestones (goal_id, due_date);
