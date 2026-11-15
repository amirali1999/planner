-- Permanent deletion from the archive (owner request 2026-11-10): an ARCHIVED goal can be deleted together with
-- everything below it (key results, check-ins, milestones, its history, its focus items and their tasks).
-- Check-ins and goal history stay append-only for every goal that is NOT archived: the no-delete triggers are
-- recreated with a WHEN clause that only lets rows of an archived goal be deleted. The no-update triggers are unchanged.

DROP TRIGGER trg_kr_checkins_no_delete;
CREATE TRIGGER trg_kr_checkins_no_delete BEFORE DELETE ON key_result_checkins
WHEN NOT EXISTS (
    SELECT 1 FROM key_results kr JOIN goals g ON g.id = kr.goal_id
    WHERE kr.id = OLD.key_result_id AND g.archived_at IS NOT NULL)
BEGIN
    SELECT RAISE(ABORT, 'key_result_checkins is append-only');
END;

DROP TRIGGER trg_goal_history_no_delete;
CREATE TRIGGER trg_goal_history_no_delete BEFORE DELETE ON goal_history
WHEN NOT EXISTS (SELECT 1 FROM goals g WHERE g.id = OLD.goal_id AND g.archived_at IS NOT NULL)
BEGIN
    SELECT RAISE(ABORT, 'goal_history is append-only');
END;
