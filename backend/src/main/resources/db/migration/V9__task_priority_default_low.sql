-- Every task has a priority (low | medium | high); "no priority" no longer exists. Tasks created before this rule get
-- the default "low". Forward-only; no other column changes. (The application enforces non-null from now on.)
UPDATE tasks SET priority = 'low' WHERE priority IS NULL;
