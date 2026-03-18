package kr.gilmok.api.queue.scheduler;

import io.lettuce.core.RedisCommandTimeoutException;
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
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Component
@RequiredArgsConstructor
public class AdmissionScheduler {

    private static final long ADMISSION_LOCK_TTL_MS = 15000;
    private static final String ROUTING_DISABLED = "ROUTING_DISABLED";

    private final QueueService queueService;
    private final EventRepository eventRepository;
    private final QueueRedisRepository queueRedisRepository;
    private final PolicyCacheRepository policyCacheRepository;
    private final MeterRegistry meterRegistry;

    private final AtomicLong lastSuccessfulRunMs = new AtomicLong(System.currentTimeMillis());

    @Value("${queue.admission-rps:10}")
    private int defaultAdmissionRps;

    @Scheduled(fixedDelay = 1000)
    @CircuitBreaker(name = "redis-admission", fallbackMethod = "processAdmissionFallback")
    public void processAdmission() {
        List<Event> openEvents = eventRepository.findByStatusOrderByStartsAtDesc(EventStatus.OPEN);
        boolean anySuccess = false;

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

                int rps = defaultAdmissionRps;
                int maxConcurrency = 0;
                Optional<PolicyCacheDto> policyOpt = policyCacheRepository.find(event.getId());
                if (policyOpt.isPresent() && policyOpt.get().exists()) {
                    PolicyCacheDto policy = policyOpt.get();
                    if (ROUTING_DISABLED.equals(policy.gateMode())) {
                        log.debug("Admission skipped (ROUTING_DISABLED): eventId={}", eventId);
                        continue;
                    }
                    if (policy.admissionRps() > 0) rps = policy.admissionRps();
                    maxConcurrency = policy.admissionConcurrency();
                }

                queueService.runAdmissionCycle(eventId, rps, maxConcurrency);
                anySuccess = true;

            } catch (RedisConnectionFailureException | RedisCommandTimeoutException e) {
                // ✅ Redis 예외는 re-throw → Circuit Breaker가 실패로 카운팅
                log.error("Admission Redis failure for eventId={}", eventId, e);
                throw e;
            } catch (Exception e) {
                // ✅ 그 외 예외는 기존처럼 삼킴 (이벤트 단위 격리 유지)
                log.error("Admission processing failed for eventId={}", eventId, e);
            } finally {
                if (locked) queueRedisRepository.unlock(eventId, lockValue);
            }
        }

        if (anySuccess || openEvents.isEmpty()) {
            lastSuccessfulRunMs.set(System.currentTimeMillis());
        }
    }

    // ✅ Circuit OPEN 상태일 때 여기로 빠짐 (Redis 장애 중 매초 로그 폭발 방지)
    private void processAdmissionFallback(Throwable t) {
        log.warn("[AdmissionScheduler] Circuit OPEN - Redis unavailable, skipping admission cycle. reason={}",
                t.getMessage());
    }

    public long getLastSuccessfulRunMs() {
        return lastSuccessfulRunMs.get();
    }
}