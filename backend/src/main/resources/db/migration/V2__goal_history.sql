-- MVP v0: minimal, append-only audit of Goal lifecycle changes (docs/DATA_MODEL.md goal_history).
-- before_json / after_json hold only the changed fields, keyed by API field name.
CREATE TABLE goal_history (
    id          TEXT PRIMARY KEY NOT NULL CHECK (length(id) = 36),
    goal_id     TEXT NOT NULL REFERENCES goals (id),
    event_type  TEXT NOT NULL CHECK (event_type IN ('details_changed', 'status_changed', 'parent_changed', 'archived', 'unarchived')),
    before_json TEXT NOT NULL CHECK (json_valid(before_json)),
    after_json  TEXT NOT NULL CHECK (json_valid(after_json)),
    created_at  TEXT NOT NULL CHECK (created_at GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]T[0-9][0-9]:[0-9][0-9]:[0-9][0-9].[0-9][0-9][0-9]Z')
);
CREATE INDEX idx_goal_history_goal ON goal_history (goal_id, created_at, id);

CREATE TRIGGER trg_goal_history_no_update BEFORE UPDATE ON goal_history
BEGIN
    SELECT RAISE(ABORT, 'goal_history is append-only');
END;
CREATE TRIGGER trg_goal_history_no_delete BEFORE DELETE ON goal_history
BEGIN
    SELECT RAISE(ABORT, 'goal_history is append-only');
END;
