package kr.gilmok.api.queue.scheduler;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import kr.gilmok.api.event.entity.Event;
import kr.gilmok.api.event.entity.EventStatus;
import kr.gilmok.api.event.repository.EventRepository;
import kr.gilmok.api.policy.dto.PolicyCacheDto;
import kr.gilmok.api.policy.repository.PolicyCacheRepository;
import kr.gilmok.api.policy.vo.BlockRules;
import kr.gilmok.api.queue.repository.QueueRedisRepository;
import kr.gilmok.api.queue.service.QueueService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdmissionSchedulerTest {

    @Mock
    private QueueService queueService;

    @Mock
    private EventRepository eventRepository;

    @Mock
    private QueueRedisRepository queueRedisRepository;

    @Mock
    private PolicyCacheRepository policyCacheRepository;

    private final MeterRegistry meterRegistry = new SimpleMeterRegistry();

    private AdmissionScheduler admissionScheduler;

    @BeforeEach
    void setUp() {
        admissionScheduler = new AdmissionScheduler(
                queueService, eventRepository, queueRedisRepository,
                policyCacheRepository, meterRegistry);
        ReflectionTestUtils.setField(admissionScheduler, "defaultAdmissionRps", 10);
    }

    private Event createEvent(Long id) {
        Event event = mock(Event.class);
        given(event.getId()).willReturn(id);
        return event;
    }

    @Test
    @DisplayName("락 획득 시 runAdmissionCycle이 실행된다")
    void processAdmission_lockAcquired_runsCycle() {
        // given
        Event event = createEvent(1L);
        given(eventRepository.findByStatusOrderByStartsAtDesc(EventStatus.OPEN))
                .willReturn(List.of(event));
        given(queueRedisRepository.tryLock(eq("1"), anyString(), eq(5000L)))
                .willReturn(true);
        given(queueRedisRepository.unlock(eq("1"), anyString()))
                .willReturn(true);
        given(policyCacheRepository.find(1L)).willReturn(Optional.empty());

        // when
        admissionScheduler.processAdmission();

        // then
        verify(queueService).runAdmissionCycle("1", 10, 0);
        verify(queueRedisRepository).unlock(eq("1"), anyString());
    }

    @Test
    @DisplayName("락 실패 시 runAdmissionCycle이 스킵되고 메트릭이 증가한다")
    void processAdmission_lockFailed_skipsCycleAndIncrementsMetric() {
        // given
        Event event = createEvent(1L);
        given(eventRepository.findByStatusOrderByStartsAtDesc(EventStatus.OPEN))
                .willReturn(List.of(event));
        given(queueRedisRepository.tryLock(eq("1"), anyString(), eq(5000L)))
                .willReturn(false);

        // when
        admissionScheduler.processAdmission();

        // then
        verify(queueService, never()).runAdmissionCycle(anyString(), anyInt(), anyInt());
        verify(queueRedisRepository, never()).unlock(anyString(), anyString());
        double skipCount = meterRegistry.get("queue.admission.lock.skipped")
                .tag("eventId", "1")
                .counter()
                .count();
        assertThat(skipCount).isEqualTo(1.0);
    }

    @Test
    @DisplayName("예외 발생 시에도 unlock이 호출된다")
    void processAdmission_exceptionThrown_unlockStillCalled() {
        // given
        Event event = createEvent(1L);
        given(eventRepository.findByStatusOrderByStartsAtDesc(EventStatus.OPEN))
                .willReturn(List.of(event));
        given(queueRedisRepository.tryLock(eq("1"), anyString(), eq(5000L)))
                .willReturn(true);
        given(policyCacheRepository.find(1L)).willReturn(Optional.empty());
        doThrow(new RuntimeException("test error"))
                .when(queueService).runAdmissionCycle("1", 10, 0);

        // when
        admissionScheduler.processAdmission();

        // then
        verify(queueRedisRepository).unlock(eq("1"), anyString());
    }

    @Test
    @DisplayName("gateMode=ROUTING_DISABLED이면 admission cycle이 스킵된다")
    void processAdmission_routingDisabled_skipsCycle() {
        // given
        Event event = createEvent(1L);
        given(eventRepository.findByStatusOrderByStartsAtDesc(EventStatus.OPEN))
                .willReturn(List.of(event));
        given(queueRedisRepository.tryLock(eq("1"), anyString(), eq(5000L)))
                .willReturn(true);
        given(queueRedisRepository.unlock(eq("1"), anyString()))
                .willReturn(true);

        PolicyCacheDto disabledPolicy = new PolicyCacheDto(
                true, 1L, 20, 500, 1L,
                BlockRules.empty(), 0, 10, "ROUTING_DISABLED");
        given(policyCacheRepository.find(1L)).willReturn(Optional.of(disabledPolicy));

        // when
        admissionScheduler.processAdmission();

        // then
        verify(queueService, never()).runAdmissionCycle(anyString(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("Policy에 admissionRps/Concurrency 설정 시 해당 값으로 전달된다")
    void processAdmission_withPolicy_usesValues() {
        // given
        Event event = createEvent(1L);
        given(eventRepository.findByStatusOrderByStartsAtDesc(EventStatus.OPEN))
                .willReturn(List.of(event));
        given(queueRedisRepository.tryLock(eq("1"), anyString(), eq(5000L)))
                .willReturn(true);
        given(queueRedisRepository.unlock(eq("1"), anyString()))
                .willReturn(true);

        PolicyCacheDto policy = new PolicyCacheDto(
                true, 1L, 20, 500, 1L,
                BlockRules.empty(), 0, 10, "ROUTING_ENABLED");
        given(policyCacheRepository.find(1L)).willReturn(Optional.of(policy));

        // when
        admissionScheduler.processAdmission();

        // then
        verify(queueService).runAdmissionCycle("1", 20, 500);
    }
}
