package com.lifeplanner.service;
import com.lifeplanner.exception.ApiException;
import com.lifeplanner.model.enums.ActivityCategory;
import com.lifeplanner.model.srv.ActivityLogSrv;
import com.lifeplanner.model.srv.ActivityTimerSrv;
import com.lifeplanner.model.vo.CreateActivityLogVO;
import com.lifeplanner.model.vo.StartActivityTimerVO;
import com.lifeplanner.repository.ActivityTimerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;
@Service
public class ActivityTimerService {
 private final ActivityTimerRepository repository;
 private final ActivityLogService logs;
 private final Clock clock;
 public ActivityTimerService(ActivityTimerRepository repository,ActivityLogService logs,Clock clock) {
  this.repository=repository; this.logs=logs; this.clock=clock;
 }
 public ActivityTimerSrv get() { return repository.get().orElse(null); }
 @Transactional
 public ActivityTimerSrv start(StartActivityTimerVO request) {
  if(get()!=null) throw new ApiException.ConflictException("timer_running","ابتدا تایمر جاری را متوقف کنید.",Map.of());
  try { ZoneId.of(request.timeZone()); } catch(DateTimeException e) {
   throw new ApiException.ValidationException("منطقه زمانی معتبر نیست.",Map.of("timeZone","invalid_value"));
  }
  ActivityTimerSrv timer=new ActivityTimerSrv(request.title().trim(),clock.instant(),request.timeZone(),request.plannedMinutes());
  for(var interval:ActivityTimerIntervals.split(timer,ActivityTimerIntervals.protectedFinish(timer,clock.instant()))) {
   logs.requireFreeForTimer(interval.date(),interval.startMinute(),interval.endMinute());
  }
  repository.insert(timer); return timer;
 }
 @Transactional
 public List<ActivityLogSrv> stop() {
  ActivityTimerSrv timer=get();
  if(timer==null) return List.of();
  Instant now=clock.instant();
  if(now.isBefore(timer.startedAt())) throw new ApiException.ConflictException("clock_changed","ساعت سیستم از زمان شروع عقب‌تر است.",Map.of());
  Instant finishInstant=ActivityTimerIntervals.actualFinish(timer,now);
  List<ActivityLogSrv> result=new ArrayList<>();
  for(var interval:ActivityTimerIntervals.split(timer,finishInstant)) {
   if(timer.plannedMinutes()==null) {
    var conflict=logs.firstOverlapForTimer(interval.date(),interval.startMinute(),interval.endMinute());
    if(conflict.isPresent()) {
     int freeEnd=Math.min(interval.endMinute(),conflict.get().startMinute());
     if(freeEnd>interval.startMinute()) {
      result.add(logs.createFromTimer(new CreateActivityLogVO(interval.date(),timer.title(),ActivityCategory.PERSONAL,
       interval.startMinute(),freeEnd,null)));
     }
     repository.delete(); return result;
    }
   }
   result.add(logs.createFromTimer(new CreateActivityLogVO(interval.date(),timer.title(),ActivityCategory.PERSONAL,
    interval.startMinute(),interval.endMinute(),null)));
  }
  repository.delete(); return result;
 }
 @Transactional
 public void cancel() { repository.delete(); }
}
