package kr.gilmok.api.queue.scheduler;

import kr.gilmok.api.event.entity.Event;
import kr.gilmok.api.event.entity.EventStatus;
import kr.gilmok.api.event.repository.EventRepository;
import kr.gilmok.api.queue.repository.QueueRedisRepository;
import kr.gilmok.api.queue.service.QueueService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

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

    @InjectMocks
    private AdmissionScheduler admissionScheduler;

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
        given(queueRedisRepository.tryLock(eq("1"), anyString(), eq(900L)))
                .willReturn(true);
        given(queueRedisRepository.unlock(eq("1"), anyString()))
                .willReturn(true);

        // when
        admissionScheduler.processAdmission();

        // then
        verify(queueService).runAdmissionCycle("1");
        verify(queueRedisRepository).unlock(eq("1"), anyString());
    }

    @Test
    @DisplayName("락 실패 시 runAdmissionCycle이 스킵된다")
    void processAdmission_lockFailed_skipsCycle() {
        // given
        Event event = createEvent(1L);
        given(eventRepository.findByStatusOrderByStartsAtDesc(EventStatus.OPEN))
                .willReturn(List.of(event));
        given(queueRedisRepository.tryLock(eq("1"), anyString(), eq(900L)))
                .willReturn(false);

        // when
        admissionScheduler.processAdmission();

        // then
        verify(queueService, never()).runAdmissionCycle(anyString());
        verify(queueRedisRepository, never()).unlock(anyString(), anyString());
    }

    @Test
    @DisplayName("예외 발생 시에도 unlock이 호출된다")
    void processAdmission_exceptionThrown_unlockStillCalled() {
        // given
        Event event = createEvent(1L);
        given(eventRepository.findByStatusOrderByStartsAtDesc(EventStatus.OPEN))
                .willReturn(List.of(event));
        given(queueRedisRepository.tryLock(eq("1"), anyString(), eq(900L)))
                .willReturn(true);
        doThrow(new RuntimeException("test error"))
                .when(queueService).runAdmissionCycle("1");

        // when
        admissionScheduler.processAdmission();

        // then
        verify(queueRedisRepository).unlock(eq("1"), anyString());
    }
}
