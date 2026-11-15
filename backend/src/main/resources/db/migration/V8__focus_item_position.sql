-- Manual order of the focus items inside one goal (goals board: drag handle, up/down). 1 = top.
-- NULL for items without a goal. Forward-only: existing linked items keep their current order (creation time, id).
ALTER TABLE focus_items ADD COLUMN position INTEGER CHECK (position IS NULL OR (typeof(position) = 'integer' AND position >= 1));

UPDATE focus_items
SET position = (
    SELECT r.rn FROM (
        SELECT id, ROW_NUMBER() OVER (PARTITION BY goal_id ORDER BY created_at, id) AS rn
        FROM focus_items
        WHERE goal_id IS NOT NULL
    ) r
    WHERE r.id = focus_items.id)
WHERE goal_id IS NOT NULL;

CREATE INDEX idx_focus_items_goal_position ON focus_items (goal_id, position);
