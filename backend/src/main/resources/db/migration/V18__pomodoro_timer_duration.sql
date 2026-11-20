ALTER TABLE activity_timers
ADD COLUMN planned_minutes INTEGER
CHECK(planned_minutes IS NULL OR (typeof(planned_minutes)='integer' AND planned_minutes BETWEEN 1 AND 120));
