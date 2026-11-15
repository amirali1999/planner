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
  assertThat(service.start(new StartActivityTimerVO(" مطالعه ","Asia/Tehran")).startedAt()).isEqualTo(now);
  verifyNoInteractions(logs);
  when(repository.get()).thenReturn(Optional.of(new ActivityTimerSrv("x",now,"UTC")));
  assertThatThrownBy(()->service.start(new StartActivityTimerVO("x","UTC"))).isInstanceOf(ApiException.ConflictException.class);
 }
 @Test void stopSplitsAtMidnightAndDoesNotWriteAgainWhenStopped() {
  var repository=mock(ActivityTimerRepository.class); var logs=mock(ActivityLogService.class);
  when(repository.get()).thenReturn(Optional.of(new ActivityTimerSrv("x",Instant.parse("2026-11-14T20:25:00Z"),"Asia/Tehran")));
  var service=new ActivityTimerService(repository,logs,Clock.fixed(Instant.parse("2026-11-14T20:40:00Z"),ZoneOffset.UTC));
  service.stop();
  var args=ArgumentCaptor.forClass(CreateActivityLogVO.class); verify(logs,times(2)).create(args.capture());
  assertThat(args.getAllValues().get(0).date()).isEqualTo(LocalDate.of(2026,11,14));
  assertThat(args.getAllValues().get(0).startMinute()).isEqualTo(1435);
  assertThat(args.getAllValues().get(0).endMinute()).isEqualTo(1440);
  assertThat(args.getAllValues().get(1).endMinute()).isEqualTo(10);
  verify(repository).delete();
  when(repository.get()).thenReturn(Optional.empty()); service.stop();
  verify(logs,times(2)).create(any());
 }
 @Test void failedStopPreservesTimerAndImmediateStopHasOneMinute() {
  var repository=mock(ActivityTimerRepository.class); var logs=mock(ActivityLogService.class);
  Instant now=Instant.parse("2026-11-14T08:00:00Z");
  when(repository.get()).thenReturn(Optional.of(new ActivityTimerSrv("x",now,"UTC")));
  var service=new ActivityTimerService(repository,logs,Clock.fixed(now,ZoneOffset.UTC));
  when(logs.create(any())).thenThrow(new ApiException.ConflictException("activity_log_overlap","overlap",java.util.Map.of()));
  assertThatThrownBy(service::stop).isInstanceOf(ApiException.ConflictException.class);
  verify(repository,never()).delete();
  var args=ArgumentCaptor.forClass(CreateActivityLogVO.class);verify(logs).create(args.capture());
  assertThat(args.getValue().endMinute()-args.getValue().startMinute()).isEqualTo(1);
 }
}
