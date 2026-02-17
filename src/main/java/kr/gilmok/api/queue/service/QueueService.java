package kr.gilmok.api.queue.service;

import io.micrometer.core.instrument.MeterRegistry;
import kr.gilmok.api.queue.QueueStatus;
import kr.gilmok.api.queue.dto.QueueRegisterRequest;
import kr.gilmok.api.queue.dto.QueueRegisterResponse;
import kr.gilmok.api.queue.dto.QueueStatusResponse;
import kr.gilmok.api.queue.repository.QueueRedisRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service
@RequiredArgsConstructor
public class QueueService {

    private final QueueRedisRepository queueRedisRepository;
    private final MeterRegistry meterRegistry; // Micrometer 레지스트리

    // 메트릭 값을 담을 변수 (AtomicLong 사용)
    private final AtomicLong queueSizeGauge = new AtomicLong(0);
    private final AtomicLong admittedSizeGauge = new AtomicLong(0);

    @Value("${queue.admission-rps:10}")
    private int admissionRps;

    @Value("${queue.admitted-ttl-seconds:300}")
    private int admittedTtlSeconds;

    @Value("${queue.grace-period-seconds:180}")
    private int gracePeriodSeconds;

    @PostConstruct
    void init() { // 메트릭 등록 및 초기화
        validateConfig();

        meterRegistry.gauge("queue.waiting.size", this.queueSizeGauge);
        meterRegistry.gauge("queue.admitted.size", this.admittedSizeGauge);
    }

    @PostConstruct
    void validateConfig() {
        if (admissionRps <= 0) {
            throw new IllegalStateException("queue.admission-rps must be positive, but was: " + admissionRps);
        }
        if (admittedTtlSeconds <= 0) {
            throw new IllegalStateException("queue.admitted-ttl-seconds must be positive, but was: " + admittedTtlSeconds);
        }
        if (gracePeriodSeconds <= 0) {
            throw new IllegalStateException("queue.grace-period-seconds must be positive, but was: " + gracePeriodSeconds);
        }
    }

    public QueueRegisterResponse register(QueueRegisterRequest request) {
        String eventId = request.getEventId();
        String sessionKey = request.getSessionKey();

        // Check if session already has a queueKey (re-entry)
        String existingQueueKey = queueRedisRepository.findQueueKeyBySession(eventId, sessionKey);
        if (existingQueueKey != null) {
            Long rank = queueRedisRepository.getRank(eventId, existingQueueKey);
            if (rank != null) {
                long position = rank + 1;
                long etaSeconds = calculateEta(eventId, position);
                queueRedisRepository.updateHeartbeat(eventId, existingQueueKey);
                return new QueueRegisterResponse(existingQueueKey, position, etaSeconds);
            }
            // Already admitted — return position 0
            if (queueRedisRepository.isAdmitted(eventId, existingQueueKey)) {
                return new QueueRegisterResponse(existingQueueKey, 0, 0);
            }
        }

        // New registration
        String queueKey = UUID.randomUUID().toString();
        double score = System.currentTimeMillis();
        queueRedisRepository.register(eventId, sessionKey, queueKey, score);
        queueRedisRepository.updateHeartbeat(eventId, queueKey);

        Long rank = queueRedisRepository.getRank(eventId, queueKey);
        long position = rank != null ? rank + 1 : 1;
        long etaSeconds = calculateEta(eventId, position);

        log.info("Queue registered: eventId={}, sessionKey={}, queueKey={}, position={}",
                eventId, sessionKey, queueKey, position);

        return new QueueRegisterResponse(queueKey, position, etaSeconds);
    }

    public QueueStatusResponse getStatus(String eventId, String queueKey) {


        // Check if admitted
        if (queueRedisRepository.isAdmitted(eventId, queueKey)) {
            return new QueueStatusResponse(QueueStatus.ADMITTABLE, 0, 0, 0, 0);
        }

        // Check if still waiting
        Long rank = queueRedisRepository.getRank(eventId, queueKey);
        if (rank != null) {
            // Update heartbeat for grace period tracking
            queueRedisRepository.updateHeartbeat(eventId, queueKey);
            long position = rank + 1;
            long total = queueRedisRepository.getQueueSize(eventId);
            long etaSeconds = calculateEta(eventId, position);
            long pollAfterMs = calculatePollInterval(position);
            return new QueueStatusResponse(QueueStatus.WAITING, position, total, etaSeconds, pollAfterMs);
        }

        // Neither in queue nor admitted → expired
        return new QueueStatusResponse(QueueStatus.EXPIRED, 0, 0, 0, 0);
    }

    public void expireAdmitted(String eventId) {
        long cutoffMs = System.currentTimeMillis() - (admittedTtlSeconds * 1000L);
        long removed = queueRedisRepository.removeExpiredAdmitted(eventId, cutoffMs);
        if (removed > 0) {
            log.info("Expired {} admitted users: eventId={}", removed, eventId);
        }
    }

    public void processAdmission(String eventId) {
        long queueSize = queueRedisRepository.getQueueSize(eventId);

        queueSizeGauge.set(queueSize);

        long admittedSize = queueRedisRepository.getAdmittedCount(eventId);
        admittedSizeGauge.set(admittedSize);

        if (queueSize == 0) {
            return;
        }

        long tokensAvailable = queueRedisRepository.consumeTokens(eventId, admissionRps, queueSize);
        if (tokensAvailable > 0) {
            long admittedCount = queueRedisRepository.admitFromHead(eventId, tokensAvailable);
            if (admittedCount > 0) {
                queueRedisRepository.recordAdmission(eventId, admittedCount);
            }
            log.info("Admitted {} users from queue: eventId={}", admittedCount, eventId);
        }
    }

    public void cleanupGracePeriod(String eventId) {
        long gracePeriodMs = gracePeriodSeconds * 1000L;
        long removed = queueRedisRepository.cleanupGracePeriod(eventId, gracePeriodMs);
        if (removed > 0) {
            log.info("Grace period cleanup removed {} stale users: eventId={}", removed, eventId);
        }
    }

    private long calculateEta(String eventId, long position) {
        Double avgRps = queueRedisRepository.getMovingAverageRps(eventId, 60_000);
        if (avgRps != null && avgRps > 0) {
            return (long) (position / avgRps);
        }
        return position / admissionRps;
    }

    private long calculatePollInterval(long position) {
        if (position >= 1000) {
            return 5000;
        } else if (position >= 100) {
            return 3000;
        } else {
            return 1000;
        }
    }
}
