package com.lifeplanner.repository;
import com.lifeplanner.model.srv.ActivityTimerSrv;
import com.lifeplanner.service.CurrentUser;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import java.time.Instant;
import java.util.Optional;
@Repository
public class ActivityTimerRepository {
 private final JdbcClient db;
 private final CurrentUser user;
 public ActivityTimerRepository(JdbcClient db, CurrentUser user) { this.db=db; this.user=user; }
 public Optional<ActivityTimerSrv> get() {
  return db.sql("SELECT title,started_at,time_zone,planned_minutes FROM activity_timers WHERE user_id=?").param(user.id())
   .query((rs,n)->new ActivityTimerSrv(rs.getString(1),Instant.parse(rs.getString(2)),rs.getString(3),
    rs.getObject(4)==null ? null : rs.getInt(4))).optional();
 }
 public void insert(ActivityTimerSrv timer) {
  db.sql("INSERT INTO activity_timers(user_id,title,started_at,time_zone,planned_minutes) VALUES (?,?,?,?,?)")
   .params(user.id(),timer.title(),timer.startedAt().toString(),timer.timeZone(),timer.plannedMinutes()).update();
 }
 public void delete() { db.sql("DELETE FROM activity_timers WHERE user_id=?").param(user.id()).update(); }
}
