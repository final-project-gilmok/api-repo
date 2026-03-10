package kr.gilmok.api.queue.scheduler;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import kr.gilmok.api.event.entity.Event;
import kr.gilmok.api.event.entity.EventStatus;
import kr.gilmok.api.event.repository.EventRepository;
import kr.gilmok.api.policy.dto.PolicyCacheDto;
import kr.gilmok.api.policy.repository.PolicyCacheRepository;
import kr.gilmok.api.queue.repository.QueueRedisRepository;
import kr.gilmok.api.queue.service.QueueService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class AdmissionScheduler {

    private static final long ADMISSION_LOCK_TTL_MS = 5000;

    private static final String ROUTING_DISABLED = "ROUTING_DISABLED";

    private final QueueService queueService;
    private final EventRepository eventRepository;
    private final QueueRedisRepository queueRedisRepository;
    private final PolicyCacheRepository policyCacheRepository;
    private final MeterRegistry meterRegistry;

    @Value("${queue.admission-rps:10}")
    private int defaultAdmissionRps;

    @Scheduled(fixedDelay = 1000)
    public void processAdmission() {
        List<Event> openEvents = eventRepository.findByStatusOrderByStartsAtDesc(EventStatus.OPEN);
        for (Event event : openEvents) {
            String eventId = String.valueOf(event.getId());
            String lockValue = UUID.randomUUID().toString();
            boolean locked = false;
            try {
                locked = queueRedisRepository.tryLock(eventId, lockValue, ADMISSION_LOCK_TTL_MS);
                if (!locked) {
                    Counter.builder("queue.admission.lock.skipped")
                            .tag("eventId", eventId)
                            .register(meterRegistry)
                            .increment();
                    continue;
                }

                // Policy 조회 → gateMode, rps, concurrency 결정
                int rps = defaultAdmissionRps;
                int maxConcurrency = 0;
                Optional<PolicyCacheDto> policyOpt = policyCacheRepository.find(event.getId());
                if (policyOpt.isPresent() && policyOpt.get().exists()) {
                    PolicyCacheDto policy = policyOpt.get();
                    if (ROUTING_DISABLED.equals(policy.gateMode())) {
                        log.debug("Admission skipped (ROUTING_DISABLED): eventId={}", eventId);
                        continue;
                    }
                    if (policy.admissionRps() > 0) {
                        rps = policy.admissionRps();
                    }
                    maxConcurrency = policy.admissionConcurrency();
                }

                queueService.runAdmissionCycle(eventId, rps, maxConcurrency);
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
