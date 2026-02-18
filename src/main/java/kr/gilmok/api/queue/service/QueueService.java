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

    // [핵심 변경] 이벤트 ID를 키로 하는 맵(Map)을 사용!
    private final Map<String, AtomicLong> waitingQueueSizes = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> admittedQueueSizes = new ConcurrentHashMap<>();

    @Value("${queue.admission-rps:10}")
    private int admissionRps;

    @Value("${queue.admitted-ttl-seconds:300}")
    private int admittedTtlSeconds;

    @Value("${queue.grace-period-seconds:180}")
    private int gracePeriodSeconds;

    // [추가 1] 앱 시작 시 초기화를 위해 기본 이벤트 ID 주입
    @Value("${queue.default-event-id:default}")
    private String defaultEventId;

    @PostConstruct
    public void init() {
        // 1. 설정값 검증
        if (admissionRps <= 0) throw new IllegalStateException("queue.admission-rps must be positive");
        if (admittedTtlSeconds <= 0) throw new IllegalStateException("queue.admitted-ttl-seconds must be positive");
        if (gracePeriodSeconds <= 0) throw new IllegalStateException("queue.grace-period-seconds must be positive");

        // 2. [중요] 앱 시작 시 기본 이벤트에 대한 메트릭 초기화 (0 -> 실제값 동기화)
        // 이걸 안 하면 재시작 직후에는 '0'으로 뜨다가 누군가 접속해야 갱신됩니다.
        updateMetrics(defaultEventId);
        log.info("Initialized metrics for default eventId: {}", defaultEventId);
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

    private void updateMetrics(String eventId) {
        // 1. Redis에서 현재 값 조회
        Long waitingSize = queueRedisRepository.getQueueSize(eventId);
        Long admittedSize = queueRedisRepository.getAdmittedCount(eventId);

        // 2. 게이지 가져오기 (없으면 안전하게 생성)
        AtomicLong waitingGauge = waitingQueueSizes.computeIfAbsent(eventId,
                id -> registerGauge("queue.waiting.size", "Number of users waiting in queue", id));

        AtomicLong admittedGauge = admittedQueueSizes.computeIfAbsent(eventId,
                id -> registerGauge("queue.admitted.size", "Number of admitted users", id));

        // 3. 값 갱신 (null 체크 포함)
        waitingGauge.set(waitingSize != null ? waitingSize : 0);
        admittedGauge.set(admittedSize != null ? admittedSize : 0);
    }

    // [신규] 게이지 등록 도우미 메서드 (코드 중복 제거)
    private AtomicLong registerGauge(String name, String description, String eventId) {
        AtomicLong gauge = new AtomicLong(0);
        Gauge.builder(name, gauge, AtomicLong::doubleValue)
                .description(description)
                .tag("eventId", eventId)
                .register(meterRegistry);
        return gauge;
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
