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
  ActivityTimerSrv timer=new ActivityTimerSrv(request.title().trim(),clock.instant(),request.timeZone());
  repository.insert(timer); return timer;
 }
 @Transactional
 public List<ActivityLogSrv> stop() {
  ActivityTimerSrv timer=get();
  if(timer==null) return List.of();
  ZoneId zone=ZoneId.of(timer.timeZone());
  var start=timer.startedAt().atZone(zone);
  var finish=clock.instant().atZone(zone);
  if(finish.isBefore(start)) throw new ApiException.ConflictException("clock_changed","ساعت سیستم از زمان شروع عقب‌تر است.",Map.of());
  List<ActivityLogSrv> result=new ArrayList<>();
  for(LocalDate day=start.toLocalDate(); !day.isAfter(finish.toLocalDate()); day=day.plusDays(1)) {
   int from=day.equals(start.toLocalDate()) ? start.getHour()*60+start.getMinute() : 0;
   int to=day.equals(finish.toLocalDate()) ? finish.getHour()*60+finish.getMinute() : 1440;
   if(day.equals(start.toLocalDate()) && day.equals(finish.toLocalDate())) to=Math.max(from+1,to);
   if(to>from) result.add(logs.create(new CreateActivityLogVO(day,timer.title(),ActivityCategory.PERSONAL,from,to,null)));
  }
  repository.delete(); return result;
 }
}
