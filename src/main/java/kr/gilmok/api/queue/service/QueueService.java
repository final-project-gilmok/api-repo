package kr.gilmok.api.queue.service;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import kr.gilmok.api.queue.QueueStatus;
import kr.gilmok.api.queue.dto.QueueRegisterRequest;
import kr.gilmok.api.queue.dto.QueueRegisterResponse;
import kr.gilmok.api.queue.dto.QueueStatusResponse;
import kr.gilmok.api.queue.exception.QueueErrorCode;
import kr.gilmok.api.queue.repository.QueueRedisRepository;
import kr.gilmok.common.exception.CustomException;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

<<<<<<< feat/52-queue-enhancement
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
=======
import java.util.HashMap;
>>>>>>> develop
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service
@RequiredArgsConstructor
public class QueueService {

    private final QueueRedisRepository queueRedisRepository;
    private final MeterRegistry meterRegistry;

    private final Map<String, AtomicLong> waitingQueueSizes = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> admittedQueueSizes = new ConcurrentHashMap<>();

    private static final int ETA_WINDOW_SECONDS = 60;
    private static final int SESSION_TTL_SECONDS = 600;

    @Value("${queue.admission-rps:10}")
    private int admissionRps;

    @Value("${queue.admitted-ttl-seconds:300}")
    private int admittedTtlSeconds;

    @Value("${queue.grace-period-seconds:180}")
    private int gracePeriodSeconds;

    @Value("${queue.default-event-id:default}")
    private String defaultEventId;

    @PostConstruct
    public void init() {
        if (admissionRps <= 0) throw new IllegalStateException("queue.admission-rps must be positive");
        if (admittedTtlSeconds <= 0) throw new IllegalStateException("queue.admitted-ttl-seconds must be positive");
        if (gracePeriodSeconds <= 0) throw new IllegalStateException("queue.grace-period-seconds must be positive");

        updateMetrics(defaultEventId);
        log.info("Initialized metrics for default eventId: {}", defaultEventId);
    }

    // === 1. 대기열 등록 (멱등 + admitted 차단) — 1 Redis 호출 ===

    public QueueRegisterResponse register(QueueRegisterRequest request) {
        String eventId = request.getEventId();
        String userId = request.getUserId();
        String newQueueKey = UUID.randomUUID().toString();
        double score = System.currentTimeMillis();

        List<Object> result = queueRedisRepository.registerIdempotent(
                eventId, userId, newQueueKey, score, SESSION_TTL_SECONDS);

        long isNew = toLong(result.get(0));
        String queueKey = String.valueOf(result.get(1));
        long rank = toLong(result.get(2));

        if (isNew == -1) {
            throw new CustomException(QueueErrorCode.ALREADY_ADMITTED);
        }

        if (isNew == 1) {
            updateMetrics(eventId);
        }

        long position = rank + 1;
        long etaSeconds = position / admissionRps;

        log.info("Queue registered: eventId={}, userId={}, queueKey={}, isNew={}, position={}",
                eventId, maskUserId(userId), queueKey, isNew, position);
        log.debug("Queue registered with original userId: eventId={}, userId={}", eventId, userId);

        return new QueueRegisterResponse(queueKey, position, etaSeconds);
    }

    // === 2. 대기열 상태 조회 — 1 Redis 호출 ===

    public QueueStatusResponse getStatus(String eventId, String queueKey) {
        List<Long> r = queueRedisRepository.getStatusAtomic(eventId, queueKey, ETA_WINDOW_SECONDS);

        long statusCode = r.get(0);
        long rank = r.get(1);
        long totalSize = r.get(2);
        long admitCountInWindow = r.get(3);

        if (statusCode == 1) {
            return new QueueStatusResponse(QueueStatus.ADMITTABLE, 0, 0, 0, 0);
        }

        if (statusCode == 2) {
            long position = rank + 1;
            long etaSeconds = calculateEtaFromCount(position, admitCountInWindow, ETA_WINDOW_SECONDS);
            long pollAfterMs = calculatePollInterval(position);
            return new QueueStatusResponse(QueueStatus.WAITING, position, totalSize, etaSeconds, pollAfterMs);
        }

        return new QueueStatusResponse(QueueStatus.EXPIRED, 0, 0, 0, 0);
    }

    // === 3. 통합 입장 사이클 — 반환값 기반 메트릭 (Redis 추가 호출 최소화) ===

    public void runAdmissionCycle(String eventId) {
        long admittedTtlMs = admittedTtlSeconds * 1000L;
        long graceMs = gracePeriodSeconds * 1000L;

        List<Object> result = queueRedisRepository.runAdmissionCycle(
                eventId, admissionRps, admissionRps,
                admittedTtlMs, graceMs, 100, 100
        );

        long expiredCount = toLong(result.get(0));
        long cleanedCount = toLong(result.get(1));
        long admittedCount = toLong(result.get(2));
        long waitingSize = toLong(result.get(5));
        long admittedSize = toLong(result.get(6));

        if (admittedCount > 0) {
            long epochSecond = System.currentTimeMillis() / 1000;
            queueRedisRepository.recordAdmissionRate(eventId, admittedCount, epochSecond);

            // Extract admitted member queueKeys from index 7+
            List<String> admittedMembers = new ArrayList<>();
            for (int i = 7; i < result.size(); i++) {
                admittedMembers.add(String.valueOf(result.get(i)));
            }
            if (!admittedMembers.isEmpty()) {
                queueRedisRepository.updateSessionsToAdmitted(eventId, admittedMembers, SESSION_TTL_SECONDS);
            }

            log.info("Admitted {} users from queue: eventId={}", admittedCount, eventId);
        }
        if (expiredCount > 0) {
            log.info("Expired {} admitted users: eventId={}", expiredCount, eventId);
        }
        if (cleanedCount > 0) {
            log.info("Grace period cleanup removed {} stale users: eventId={}", cleanedCount, eventId);
        }

        updateMetricsFromValues(eventId, waitingSize, admittedSize);
    }

    // === Metrics ===

    private void updateMetrics(String eventId) {
        long waitingSize = queueRedisRepository.getQueueSize(eventId);
        long admittedSize = queueRedisRepository.getAdmittedCount(eventId);
        updateMetricsFromValues(eventId, waitingSize, admittedSize);
    }

    private void updateMetricsFromValues(String eventId, long waitingSize, long admittedSize) {
        AtomicLong waitingGauge = waitingQueueSizes.computeIfAbsent(eventId,
                id -> registerGauge("queue.waiting.size", "Number of users waiting in queue", id));
        AtomicLong admittedGauge = admittedQueueSizes.computeIfAbsent(eventId,
                id -> registerGauge("queue.admitted.size", "Number of admitted users", id));

        waitingGauge.set(waitingSize);
        admittedGauge.set(admittedSize);
    }

    private AtomicLong registerGauge(String name, String description, String eventId) {
        AtomicLong gauge = new AtomicLong(0);
        Gauge.builder(name, gauge, AtomicLong::doubleValue)
                .description(description)
                .tag("eventId", eventId)
                .register(meterRegistry);
        return gauge;
    }

    // === ETA Calculation ===

    private long calculateEtaFromCount(long position, long admitCountInWindow, int windowSeconds) {
        if (admitCountInWindow > 0 && windowSeconds > 0) {
            double rps = (double) admitCountInWindow / windowSeconds;
            return (long) (position / rps);
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

<<<<<<< feat/52-queue-enhancement
    private long toLong(Object obj) {
        if (obj instanceof Long) return (Long) obj;
        if (obj instanceof Number) return ((Number) obj).longValue();
        if (obj instanceof String) return Long.parseLong((String) obj);
        return 0;
    }

    private String maskUserId(String userId) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(userId.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash).substring(0, 8);
        } catch (NoSuchAlgorithmException e) {
            return "********";
        }
=======
    // [신규 추가] 특정 이벤트의 현재 대기열 상태(사이즈) 정보 조회
    public Map<String, Long> getQueueMetricsForAi(String eventId) {
        Long waitingSize = queueRedisRepository.getQueueSize(eventId);
        Long admittedSize = queueRedisRepository.getAdmittedCount(eventId);

        // 이동평균 RPS도 AI가 분석하기 좋은 지표이므로 함께 넘겨주는 것을 추천합니다.
        Double currentRps = queueRedisRepository.getMovingAverageRps(eventId, 60_000); // 최근 1분

        Map<String, Long> metrics = new HashMap<>();
        metrics.put("waitingQueueSize", waitingSize != null ? waitingSize : 0L);
        metrics.put("admittedQueueSize", admittedSize != null ? admittedSize : 0L);
        metrics.put("currentRps", currentRps != null ? Math.round(currentRps) : 0L);

        return metrics;
>>>>>>> develop
    }
}
