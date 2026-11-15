-- Manual priority of the user's active goals (1 = highest, shown right-most on the goals board).
-- NULL for archived goals. Forward-only: existing active goals are ranked in their current display order
-- (period start, coarser period first, creation time, id) per owner, so nothing changes visually.
ALTER TABLE goals ADD COLUMN priority INTEGER CHECK (priority IS NULL OR (typeof(priority) = 'integer' AND priority >= 1));

UPDATE goals
SET priority = (
    SELECT r.rn FROM (
        SELECT id, ROW_NUMBER() OVER (
            PARTITION BY user_id
            ORDER BY period_start,
                     CASE period_type WHEN 'annual' THEN 0 WHEN 'quarterly' THEN 1 WHEN 'monthly' THEN 2 ELSE 3 END,
                     created_at, id) AS rn
        FROM goals
        WHERE archived_at IS NULL
    ) r
    WHERE r.id = goals.id)
WHERE archived_at IS NULL;

CREATE INDEX idx_goals_user_priority ON goals (user_id, priority);
