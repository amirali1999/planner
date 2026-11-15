-- Tasks put on a Saturday–Friday week without choosing a day (owner request 2026-11-12: «+ به برنامه هفتگی» on the
-- focus page). Only membership: no capacity/commitment fields. Root table → user_id; a task is on a week at most once.
-- week_start is the Saturday of the week. Forward-only.
CREATE TABLE weekly_plan_items (
    id         TEXT PRIMARY KEY NOT NULL CHECK (length(id) = 36),
    user_id    TEXT NOT NULL REFERENCES users (id),
    week_start TEXT NOT NULL CHECK (week_start GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]' AND date(week_start) IS week_start
                                    AND strftime('%w', week_start) = '6'),
    task_id    TEXT NOT NULL REFERENCES tasks (id),
    created_at TEXT NOT NULL CHECK (created_at GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]T[0-9][0-9]:[0-9][0-9]:[0-9][0-9].[0-9][0-9][0-9]Z'),
    CONSTRAINT uq_weekly_plan_items_week_task UNIQUE (user_id, week_start, task_id)
);
CREATE INDEX idx_weekly_plan_items_task_id ON weekly_plan_items (task_id);
