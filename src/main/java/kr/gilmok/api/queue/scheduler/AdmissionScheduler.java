package kr.gilmok.api.queue.scheduler;

import kr.gilmok.api.event.entity.Event;
import kr.gilmok.api.event.entity.EventStatus;
import kr.gilmok.api.event.repository.EventRepository;
import kr.gilmok.api.queue.service.QueueService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class AdmissionScheduler {

    private final QueueService queueService;
    private final EventRepository eventRepository;

    @Scheduled(fixedDelay = 1000)
    public void processAdmission() {
        List<Event> openEvents = eventRepository.findByStatusOrderByStartsAtDesc(EventStatus.OPEN);
        for (Event event : openEvents) {
            String eventId = String.valueOf(event.getId());
            try {
                queueService.expireAdmitted(eventId);
                queueService.cleanupGracePeriod(eventId);
                queueService.processAdmission(eventId);
            } catch (Exception e) {
                log.error("Admission processing failed for eventId={}", eventId, e);
            }
        }
    }
}
