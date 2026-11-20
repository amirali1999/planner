package com.lifeplanner.service;
import com.lifeplanner.model.srv.ActivityTimerSrv;
import com.lifeplanner.model.vo.CreateActivityLogVO;
import com.lifeplanner.model.vo.StartActivityTimerVO;
import com.lifeplanner.repository.ActivityTimerRepository;
import com.lifeplanner.exception.ApiException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.time.*;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class ActivityTimerServiceTest {
 @Test void startingCapturesTimeWithoutWritingLogs() {
  var repository=mock(ActivityTimerRepository.class); var logs=mock(ActivityLogService.class);
  when(repository.get()).thenReturn(Optional.empty());
  Instant now=Instant.parse("2026-11-14T08:00:00Z");
  var service=new ActivityTimerService(repository,logs,Clock.fixed(now,ZoneOffset.UTC));
  ActivityTimerSrv started=service.start(new StartActivityTimerVO(" مطالعه ","Asia/Tehran",25));
  assertThat(started.startedAt()).isEqualTo(now);
  assertThat(started.plannedMinutes()).isEqualTo(25);
  verify(logs).requireFreeForTimer(LocalDate.of(2026,11,14),690,715);
  when(repository.get()).thenReturn(Optional.of(new ActivityTimerSrv("x",now,"UTC",null)));
  assertThatThrownBy(()->service.start(new StartActivityTimerVO("x","UTC",null))).isInstanceOf(ApiException.ConflictException.class);
 }
 @Test void stopSplitsAtMidnightAndDoesNotWriteAgainWhenStopped() {
  var repository=mock(ActivityTimerRepository.class); var logs=mock(ActivityLogService.class);
  when(repository.get()).thenReturn(Optional.of(new ActivityTimerSrv("x",Instant.parse("2026-11-14T20:25:00Z"),"Asia/Tehran",null)));
  var service=new ActivityTimerService(repository,logs,Clock.fixed(Instant.parse("2026-11-14T20:40:00Z"),ZoneOffset.UTC));
  service.stop();
  var args=ArgumentCaptor.forClass(CreateActivityLogVO.class); verify(logs,times(2)).createFromTimer(args.capture());
  assertThat(args.getAllValues().get(0).date()).isEqualTo(LocalDate.of(2026,11,14));
  assertThat(args.getAllValues().get(0).startMinute()).isEqualTo(1435);
  assertThat(args.getAllValues().get(0).endMinute()).isEqualTo(1440);
  assertThat(args.getAllValues().get(1).endMinute()).isEqualTo(10);
  verify(repository).delete();
  when(repository.get()).thenReturn(Optional.empty()); service.stop();
  verify(logs,times(2)).createFromTimer(any());
 }
 @Test void failedStopPreservesTimerAndImmediateStopHasOneMinute() {
  var repository=mock(ActivityTimerRepository.class); var logs=mock(ActivityLogService.class);
  Instant now=Instant.parse("2026-11-14T08:00:00Z");
  when(repository.get()).thenReturn(Optional.of(new ActivityTimerSrv("x",now,"UTC",null)));
  var service=new ActivityTimerService(repository,logs,Clock.fixed(now,ZoneOffset.UTC));
  when(logs.createFromTimer(any())).thenThrow(new ApiException.ConflictException("activity_log_overlap","overlap",java.util.Map.of()));
  assertThatThrownBy(service::stop).isInstanceOf(ApiException.ConflictException.class);
  verify(repository,never()).delete();
  var args=ArgumentCaptor.forClass(CreateActivityLogVO.class);verify(logs).createFromTimer(args.capture());
  assertThat(args.getValue().endMinute()-args.getValue().startMinute()).isEqualTo(1);
 }
 @Test void pomodoroStopsEarlyButNeverRunsPastItsPlannedDuration() {
  var repository=mock(ActivityTimerRepository.class); var logs=mock(ActivityLogService.class);
  Instant start=Instant.parse("2026-11-14T08:00:00Z");
  when(repository.get()).thenReturn(Optional.of(new ActivityTimerSrv("focus",start,"UTC",25)));

  var early=new ActivityTimerService(repository,logs,Clock.fixed(start.plusSeconds(10*60L),ZoneOffset.UTC));
  early.stop();
  var args=ArgumentCaptor.forClass(CreateActivityLogVO.class); verify(logs).createFromTimer(args.capture());
  assertThat(args.getValue().startMinute()).isEqualTo(8*60);
  assertThat(args.getValue().endMinute()).isEqualTo(8*60+10);

  reset(logs,repository);
  when(repository.get()).thenReturn(Optional.of(new ActivityTimerSrv("focus",start,"UTC",25)));
  var late=new ActivityTimerService(repository,logs,Clock.fixed(start.plusSeconds(60*60L),ZoneOffset.UTC));
  late.stop();
  verify(logs).createFromTimer(args.capture());
  assertThat(args.getValue().endMinute()).isEqualTo(8*60+25);
 }
 @Test void startRejectsAnExistingLogAnywhereInThePlannedWindow() {
  var repository=mock(ActivityTimerRepository.class); var logs=mock(ActivityLogService.class);
  when(repository.get()).thenReturn(Optional.empty());
  Instant start=Instant.parse("2026-11-14T23:50:00Z");
  doThrow(new ApiException.ConflictException("activity_log_overlap","overlap",java.util.Map.of()))
   .when(logs).requireFreeForTimer(LocalDate.of(2026,11,15),0,15);
  var service=new ActivityTimerService(repository,logs,Clock.fixed(start,ZoneOffset.UTC));

  assertThatThrownBy(()->service.start(new StartActivityTimerVO("focus","UTC",25)))
   .isInstanceOf(ApiException.ConflictException.class);
  verify(logs).requireFreeForTimer(LocalDate.of(2026,11,14),23*60+50,1440);
  verify(logs).requireFreeForTimer(LocalDate.of(2026,11,15),0,15);
  verify(repository,never()).insert(any());
 }
 @Test void cancelDiscardsTheTimerWithoutWritingALog() {
  var repository=mock(ActivityTimerRepository.class); var logs=mock(ActivityLogService.class);
  var service=new ActivityTimerService(repository,logs,Clock.systemUTC());
  service.cancel();
  verify(repository).delete();
  verifyNoInteractions(logs);
 }
 @Test void openEndedStopEndsAtTheFirstExistingLogInsteadOfLeavingTheTimerStuck() {
  var repository=mock(ActivityTimerRepository.class); var logs=mock(ActivityLogService.class);
  Instant start=Instant.parse("2026-11-14T08:00:00Z");
  when(repository.get()).thenReturn(Optional.of(new ActivityTimerSrv("work",start,"UTC",null)));
  when(logs.firstOverlapForTimer(LocalDate.of(2026,11,14),480,540)).thenReturn(Optional.of(new com.lifeplanner.model.srv.ActivityLogSrv(
   "existing",LocalDate.of(2026,11,14),"meeting","work",510,525,start,start,null)));
  var service=new ActivityTimerService(repository,logs,Clock.fixed(start.plusSeconds(60*60L),ZoneOffset.UTC));

  service.stop();

  var args=ArgumentCaptor.forClass(CreateActivityLogVO.class); verify(logs).createFromTimer(args.capture());
  assertThat(args.getValue().startMinute()).isEqualTo(480);
  assertThat(args.getValue().endMinute()).isEqualTo(510);
  verify(repository).delete();
 }
}
