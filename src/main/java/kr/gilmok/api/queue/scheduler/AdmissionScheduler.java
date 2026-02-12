package kr.gilmok.api.queue.scheduler;

import kr.gilmok.api.queue.service.QueueService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AdmissionScheduler {

    private final QueueService queueService;

    @Value("${queue.default-event-id:default}")
    private String defaultEventId;

    @Scheduled(fixedDelay = 1000)
    public void processAdmission() {
        try {
            queueService.expireAdmitted(defaultEventId);
            queueService.cleanupGracePeriod(defaultEventId);
            queueService.processAdmission(defaultEventId);
        } catch (Exception e) {
            log.error("Admission processing failed for eventId={}", defaultEventId, e);
        }
    }
}
