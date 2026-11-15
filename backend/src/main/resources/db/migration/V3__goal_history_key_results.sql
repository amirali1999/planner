-- K-01: goal_history also audits KR structural changes (docs/DATA_MODEL.md: "selective audit for status, parent,
-- KR structural changes"). SQLite cannot alter a CHECK constraint, so the table is rebuilt; every existing row is kept.
-- The append-only triggers are dropped first and recreated on the new table.

DROP TRIGGER trg_goal_history_no_update;
DROP TRIGGER trg_goal_history_no_delete;

CREATE TABLE goal_history_v3 (
    id            TEXT PRIMARY KEY NOT NULL CHECK (length(id) = 36),
    goal_id       TEXT NOT NULL REFERENCES goals (id),
    key_result_id TEXT REFERENCES key_results (id),
    event_type    TEXT NOT NULL CHECK (event_type IN (
                      'details_changed', 'status_changed', 'parent_changed', 'archived', 'unarchived',
                      'key_result_created', 'key_result_changed', 'key_result_archived')),
    before_json   TEXT NOT NULL CHECK (json_valid(before_json)),
    after_json    TEXT NOT NULL CHECK (json_valid(after_json)),
    -- explanatory note, e.g. why a KR baseline/target/weight was recalibrated
    note          TEXT,
    created_at    TEXT NOT NULL CHECK (created_at GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]T[0-9][0-9]:[0-9][0-9]:[0-9][0-9].[0-9][0-9][0-9]Z'),
    -- KR events always name their KR; goal events never do
    CHECK ((substr(event_type, 1, 11) = 'key_result_') = (key_result_id IS NOT NULL))
);

INSERT INTO goal_history_v3 (id, goal_id, key_result_id, event_type, before_json, after_json, note, created_at)
SELECT id, goal_id, NULL, event_type, before_json, after_json, NULL, created_at
FROM goal_history
ORDER BY rowid;

DROP TABLE goal_history;
ALTER TABLE goal_history_v3 RENAME TO goal_history;

CREATE INDEX idx_goal_history_goal ON goal_history (goal_id, created_at, id);
CREATE INDEX idx_goal_history_key_result ON goal_history (key_result_id, created_at, id);

CREATE TRIGGER trg_goal_history_no_update BEFORE UPDATE ON goal_history
BEGIN
    SELECT RAISE(ABORT, 'goal_history is append-only');
END;
CREATE TRIGGER trg_goal_history_no_delete BEFORE DELETE ON goal_history
BEGIN
    SELECT RAISE(ABORT, 'goal_history is append-only');
END;
