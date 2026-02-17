package kr.gilmok.api.queue.service;

import io.micrometer.core.instrument.Gauge;
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
    private final MeterRegistry meterRegistry;

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
    void init() {
        validateConfig();
        // [유지] 게이지 등록 로직
        Gauge.builder("queue.waiting.size", this.queueSizeGauge, AtomicLong::doubleValue)
                .description("Number of users waiting in queue")
                .register(meterRegistry);
        Gauge.builder("queue.admitted.size", this.admittedSizeGauge, AtomicLong::doubleValue)
                .description("Number of admitted users")
                .register(meterRegistry);
    }

    void validateConfig() {
        if (admissionRps <= 0) throw new IllegalStateException("queue.admission-rps must be positive");
        if (admittedTtlSeconds <= 0) throw new IllegalStateException("queue.admitted-ttl-seconds must be positive");
        if (gracePeriodSeconds <= 0) throw new IllegalStateException("queue.grace-period-seconds must be positive");
    }

    // 1. 대기열 등록 (사용자 진입 시점)
    public QueueRegisterResponse register(QueueRegisterRequest request) {
        String eventId = request.getEventId();
        String sessionKey = request.getSessionKey();

        // [생략] 재진입(Re-entry) 로직은 상태 변경이 없으므로 그대로 둠...
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

        // [중요] 신규 등록 발생 -> 상태 변경됨
        String queueKey = UUID.randomUUID().toString();
        double score = System.currentTimeMillis();
        queueRedisRepository.register(eventId, sessionKey, queueKey, score);
        queueRedisRepository.updateHeartbeat(eventId, queueKey);

        // [추가] 상태가 변했으니 메트릭 갱신!
        updateMetrics(eventId);

        Long rank = queueRedisRepository.getRank(eventId, queueKey);
        long position = rank != null ? rank + 1 : 1;
        long etaSeconds = calculateEta(eventId, position);

        log.info("Queue registered: eventId={}, sessionKey={}, queueKey={}, position={}", eventId, sessionKey, queueKey, position);

        return new QueueRegisterResponse(queueKey, position, etaSeconds);
    }

    // 2. 대기열 상태 조회 (변화 없음 -> 메트릭 갱신 불필요)
    public QueueStatusResponse getStatus(String eventId, String queueKey) {
        // [생략] 조회 로직은 상태를 바꾸지 않으므로 메트릭 갱신 호출 안 함
        if (queueRedisRepository.isAdmitted(eventId, queueKey)) {
            return new QueueStatusResponse(QueueStatus.ADMITTABLE, 0, 0, 0, 0);
        }
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
        return new QueueStatusResponse(QueueStatus.EXPIRED, 0, 0, 0, 0);
    }

    // 3. 만료 처리 (사용자 이탈 시점)
    public void expireAdmitted(String eventId) {
        long cutoffMs = System.currentTimeMillis() - (admittedTtlSeconds * 1000L);
        long removed = queueRedisRepository.removeExpiredAdmitted(eventId, cutoffMs);

        if (removed > 0) {
            log.info("Expired {} admitted users: eventId={}", removed, eventId);
            // [추가] 삭제된 유저가 있다면 메트릭 갱신!
            updateMetrics(eventId);
        }
    }

    // 4. 스케줄러 입장 처리 (대기 -> 입장 이동 시점)
    public void processAdmission(String eventId) {
        long queueSize = queueRedisRepository.getQueueSize(eventId);

        if (queueSize == 0) {
            // 대기자가 없어도 갱신은 필요함 (0으로 맞춰야 하니까)
            updateMetrics(eventId);
            return;
        }

        long tokensAvailable = queueRedisRepository.consumeTokens(eventId, admissionRps, queueSize);
        if (tokensAvailable > 0) {
            long admittedCount = queueRedisRepository.admitFromHead(eventId, tokensAvailable);
            if (admittedCount > 0) {
                queueRedisRepository.recordAdmission(eventId, admittedCount);
                log.info("Admitted {} users from queue: eventId={}", admittedCount, eventId);

                // [추가] 이동이 발생했으면 메트릭 갱신!
                updateMetrics(eventId);
            }
        } else {
            // 토큰이 없어서 입장이 안 일어났어도,
            // 위에서 expireAdmitted 등에 의해 큐 사이즈가 변했을 수 있으니 안전하게 갱신
            updateMetrics(eventId);
        }
    }

    // 5. 대기열 이탈 정리 (타임아웃 시점)
    public void cleanupGracePeriod(String eventId) {
        long gracePeriodMs = gracePeriodSeconds * 1000L;
        long removed = queueRedisRepository.cleanupGracePeriod(eventId, gracePeriodMs);

        if (removed > 0) {
            log.info("Grace period cleanup removed {} stale users: eventId={}", removed, eventId);
            // [추가] 대기열에서 삭제되었으니 메트릭 갱신!
            updateMetrics(eventId);
        }
    }

    // [신규] 공통 메트릭 갱신 메서드
    // 이 메서드 하나로 모든 곳의 메트릭 정합성을 맞춥니다.
    private void updateMetrics(String eventId) {
        long waitingSize = queueRedisRepository.getQueueSize(eventId);
        long admittedSize = queueRedisRepository.getAdmittedCount(eventId);

        queueSizeGauge.set(waitingSize);
        admittedSizeGauge.set(admittedSize);
    }

    // [유지] 유틸 메서드들
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
