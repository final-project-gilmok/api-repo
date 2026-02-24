package kr.gilmok.api.queue.scheduler;

import kr.gilmok.api.event.entity.Event;
import kr.gilmok.api.event.entity.EventStatus;
import kr.gilmok.api.event.repository.EventRepository;
import kr.gilmok.api.queue.repository.QueueRedisRepository;
import kr.gilmok.api.queue.service.QueueService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class AdmissionScheduler {

    private final QueueService queueService;
    private final EventRepository eventRepository;
    private final QueueRedisRepository queueRedisRepository;

    @Scheduled(fixedDelay = 1000)
    public void processAdmission() {
        List<Event> openEvents = eventRepository.findByStatusOrderByStartsAtDesc(EventStatus.OPEN);
        for (Event event : openEvents) {
            String eventId = String.valueOf(event.getId());
            String lockValue = UUID.randomUUID().toString();
            boolean locked = false;
            try {
                locked = queueRedisRepository.tryLock(eventId, lockValue, 900);
                if (!locked) {
                    continue;
                }
                queueService.runAdmissionCycle(eventId);
            } catch (Exception e) {
                log.error("Admission processing failed for eventId={}", eventId, e);
            } finally {
                if (locked) {
                    queueRedisRepository.unlock(eventId, lockValue);
                }
            }
        }
    }
}
