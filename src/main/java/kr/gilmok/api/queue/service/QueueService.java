package kr.gilmok.api.queue.service;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import kr.gilmok.api.queue.QueueStatus;
import kr.gilmok.api.queue.dto.QueueRegisterRequest;
import kr.gilmok.api.queue.dto.QueueRegisterResponse;
import kr.gilmok.api.queue.dto.QueueStatusResponse;
import kr.gilmok.api.queue.exception.QueueErrorCode;
import kr.gilmok.api.policy.dto.PolicyCacheDto;
import kr.gilmok.api.queue.repository.QueueRedisRepository;
import kr.gilmok.common.exception.CustomException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
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

    @CircuitBreaker(name = "redis-queue", fallbackMethod = "registerFallback") // ✅ 추가
    public QueueRegisterResponse register(Long userId, QueueRegisterRequest request, PolicyCacheDto policy) {
        if (userId == null) {
            throw new IllegalArgumentException("userId must not be null");
        }
        String eventId = request.getEventId();
        String userIdStr = String.valueOf(userId);
        String newQueueKey = UUID.randomUUID().toString();
        double score = System.currentTimeMillis();

        List<Object> result = queueRedisRepository.registerIdempotent(
                eventId, userIdStr, newQueueKey, score, SESSION_TTL_SECONDS);

        long isNew = toLong(result.get(0));
        String queueKey = String.valueOf(result.get(1));
        long rank = toLong(result.get(2));

        if (isNew == -1) {
            log.info("Queue already admitted: eventId={}, userId={}, queueKey={}",
                    eventId, maskUserId(userIdStr), queueKey);
            return new QueueRegisterResponse(queueKey, 0, 0);
        }

        if (isNew == 1) {
            long waitingSize = toLong(result.get(3));
            long admittedSize = toLong(result.get(4));
            updateMetricsFromValues(eventId, waitingSize, admittedSize);
        }

        long position = rank + 1;
        int effectiveRps = resolveAdmissionRps(policy);
        long etaSeconds = position / effectiveRps;

        log.info("Queue registered: eventId={}, userId={}, queueKey={}, isNew={}, position={}",
                eventId, maskUserId(userIdStr), queueKey, isNew, position);

        return new QueueRegisterResponse(queueKey, position, etaSeconds);
    }

    // ✅ register fallback
    private QueueRegisterResponse registerFallback(Long userId, QueueRegisterRequest request,
                                                   PolicyCacheDto policy, Throwable t) {
        if (t instanceof CallNotPermittedException) {
            log.warn("[QueueService] Circuit OPEN - register rejected: eventId={}", request.getEventId());
            throw new CustomException(QueueErrorCode.QUEUE_SERVICE_UNAVAILABLE);
        }
        // 비즈니스 예외(CustomException, IllegalArgumentException 등)는 그대로 re-throw
        if (t instanceof RuntimeException) throw (RuntimeException) t;
        throw new RuntimeException(t);
    }

    // === 2. 대기열 상태 조회 — 1 Redis 호출 ===

    @CircuitBreaker(name = "redis-queue", fallbackMethod = "getStatusFallback") // ✅ 추가
    public QueueStatusResponse getStatus(String eventId, String queueKey, String username, long userId) {
        Long ownerUserId = queueRedisRepository.getQueueOwnerUserId(eventId, queueKey);
        if (ownerUserId == null || !ownerUserId.equals(userId)) {
            log.warn("Queue ownership mismatch: eventId={}, queueKey={}, userId={}", eventId, queueKey, userId);
            return new QueueStatusResponse(QueueStatus.EXPIRED, 0, 0, 0, 0, null);
        }

        List<Long> r = queueRedisRepository.getStatusAtomic(eventId, queueKey, ETA_WINDOW_SECONDS);

        if (r.size() < 4) {
            log.error("queueStatusScript returned unexpected result size={}, eventId={}", r.size(), eventId);
            return new QueueStatusResponse(QueueStatus.EXPIRED, 0, 0, 0, 0, null);
        }

        long statusCode = r.get(0);
        long rank = r.get(1);
        long totalSize = r.get(2);
        long admitCountInWindow = r.get(3);

        if (statusCode == 1) {
            return new QueueStatusResponse(QueueStatus.ADMITTABLE, 0, 0, 0, 0, null);
        }

        if (statusCode == 2) {
            long position = rank + 1;
            long etaSeconds = calculateEtaFromCount(position, admitCountInWindow, ETA_WINDOW_SECONDS);
            long pollAfterMs = calculatePollInterval(position);
            return new QueueStatusResponse(QueueStatus.WAITING, position, totalSize, etaSeconds, pollAfterMs, null);
        }

        return new QueueStatusResponse(QueueStatus.EXPIRED, 0, 0, 0, 0, null);
    }

    // ✅ getStatus fallback
    private QueueStatusResponse getStatusFallback(String eventId, String queueKey,
                                                  String username, long userId, Throwable t) {
        if (t instanceof CallNotPermittedException) {
            log.warn("[QueueService] Circuit OPEN - getStatus rejected: eventId={}", eventId);
            throw new CustomException(QueueErrorCode.QUEUE_SERVICE_UNAVAILABLE);
        }
        if (t instanceof RuntimeException) throw (RuntimeException) t;
        throw new RuntimeException(t);
    }

    // === 3. 통합 입장 사이클 ===

    // ✅ @Retryable 제거 — Circuit Breaker가 빠른 실패 담당하므로 중복 불필요
    public void runAdmissionCycle(String eventId, int rps, int maxConcurrency) {
        long admittedTtlMs = admittedTtlSeconds * 1000L;
        long graceMs = gracePeriodSeconds * 1000L;

        List<Object> result = queueRedisRepository.runAdmissionCycle(
                eventId, rps, rps,
                admittedTtlMs, graceMs, 100, 100, maxConcurrency
        );

        long expiredCount = toLong(result.get(0));
        long cleanedCount = toLong(result.get(1));
        long admittedCount = toLong(result.get(2));
        long waitingSize = toLong(result.get(5));
        long admittedSize = toLong(result.get(6));

        if (admittedCount > 0) {
            long epochSecond = System.currentTimeMillis() / 1000;
            queueRedisRepository.recordAdmissionRate(eventId, admittedCount, epochSecond);

            List<String> admittedMembers = new ArrayList<>();
            for (int i = 7; i < result.size(); i++) {
                admittedMembers.add(String.valueOf(result.get(i)));
            }
            if (!admittedMembers.isEmpty()) {
                queueRedisRepository.updateSessionsToAdmitted(eventId, admittedMembers, admittedTtlSeconds);
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

    // === 4. 대기열 검증 ===

    @CircuitBreaker(name = "redis-queue-verify", fallbackMethod = "verifyQueueAccessFallback")
    public void verifyQueueAccess(String eventId, String queueKey, Long userId) {
        Long ownerUserId = queueRedisRepository.getQueueOwnerUserId(eventId, queueKey);
        if (ownerUserId == null || !ownerUserId.equals(userId)) {
            log.warn("Queue ownership mismatch or expired: eventId={}, queueKey={}, userId={}", eventId, queueKey, userId);
            throw new CustomException(QueueErrorCode.INVALID_QUEUE_KEY);
        }

        if (!queueRedisRepository.isAdmitted(eventId, queueKey)) {
            log.warn("Queue not admitted: eventId={}, queueKey={}, userId={}", eventId, queueKey, userId);
            throw new CustomException(QueueErrorCode.NOT_ADMITTED);
        }
    }

    // ✅ verifyQueueAccess fallback
    private void verifyQueueAccessFallback(String eventId, String queueKey, Long userId, Throwable t) {
        if (t instanceof CallNotPermittedException) {
            log.warn("[QueueService] Circuit OPEN - verifyQueueAccess rejected: eventId={}", eventId);
            throw new CustomException(QueueErrorCode.QUEUE_SERVICE_UNAVAILABLE);
        }
        if (t instanceof RuntimeException) throw (RuntimeException) t;
        throw new RuntimeException(t);
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

    // === Policy Lookup ===

    private int resolveAdmissionRps(PolicyCacheDto policy) {
        if (policy != null && policy.exists() && policy.admissionRps() > 0) {
            return policy.admissionRps();
        }
        return admissionRps;
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
        if (position >= 1000) return 5000;
        else if (position >= 100) return 3000;
        else return 1000;
    }

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
    }

    // === AI용 메트릭 조회 ===

    public Map<String, Long> getQueueMetricsForAi(String eventId) {
        Map<String, Long> metrics = new HashMap<>();
        metrics.put("waitingQueueSize", 0L);
        metrics.put("admittedQueueSize", 0L);
        metrics.put("currentRps", 0L);

        try {
            Long waitingSize = queueRedisRepository.getQueueSize(eventId);
            Long admittedSize = queueRedisRepository.getAdmittedCount(eventId);
            Double currentRps = queueRedisRepository.getMovingAverageRps(eventId, 60_000);

            metrics.put("waitingQueueSize", waitingSize != null ? waitingSize : 0L);
            metrics.put("admittedQueueSize", admittedSize != null ? admittedSize : 0L);
            metrics.put("currentRps", currentRps != null ? Math.round(currentRps) : 0L);
        } catch (Exception e) {
            // ✅ Redis 장애 시 0값 기본 메트릭 반환 — AI 서비스 호출 끊기지 않음
            log.warn("[QueueService] getQueueMetricsForAi failed, returning defaults: eventId={}", eventId, e);
        }

        return metrics;
    }
}
