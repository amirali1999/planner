-- When a task became done (UTC). Set by the application on the transition to done, kept while it stays done and
-- cleared when it is reopened. The «تخته امروز» board uses it to show a finished task only on the day it was finished.
-- Tasks that are already done get their last change time as the best available estimate. Forward-only.
ALTER TABLE tasks ADD COLUMN completed_at TEXT
    CHECK (completed_at IS NULL OR completed_at GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]T[0-9][0-9]:[0-9][0-9]:[0-9][0-9].[0-9][0-9][0-9]Z');
UPDATE tasks SET completed_at = updated_at WHERE status = 'done';
CREATE INDEX idx_tasks_completed_at ON tasks (completed_at);
