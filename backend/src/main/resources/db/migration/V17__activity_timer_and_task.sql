CREATE TABLE activity_logs_new (
 id TEXT PRIMARY KEY NOT NULL CHECK(length(id)=36),
 user_id TEXT NOT NULL REFERENCES users(id),
 local_date TEXT NOT NULL CHECK(date(local_date) IS local_date),
 title TEXT NOT NULL CHECK(length(trim(title))>0),
 category TEXT NOT NULL CHECK(category IN ('work','study','rest','personal')),
 start_minute INTEGER NOT NULL CHECK(typeof(start_minute)='integer' AND start_minute>=0 AND start_minute<1440),
 end_minute INTEGER NOT NULL CHECK(typeof(end_minute)='integer' AND end_minute>start_minute AND end_minute<=1440),
 created_at TEXT NOT NULL, updated_at TEXT NOT NULL,
 task_id TEXT REFERENCES tasks(id) ON DELETE SET NULL
);
INSERT INTO activity_logs_new SELECT id,user_id,local_date,title,category,start_minute,end_minute,created_at,updated_at,NULL FROM activity_logs;
DROP TABLE activity_logs;
ALTER TABLE activity_logs_new RENAME TO activity_logs;
CREATE INDEX idx_activity_logs_user_date ON activity_logs(user_id,local_date,start_minute,end_minute);
CREATE INDEX idx_activity_logs_task ON activity_logs(task_id);
CREATE TABLE activity_timers (
 user_id TEXT PRIMARY KEY NOT NULL REFERENCES users(id),
 title TEXT NOT NULL CHECK(length(trim(title))>0),
 started_at TEXT NOT NULL,
 time_zone TEXT NOT NULL
);
