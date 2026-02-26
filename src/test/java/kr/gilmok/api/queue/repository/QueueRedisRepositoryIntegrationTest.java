package kr.gilmok.api.queue.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class QueueRedisRepositoryIntegrationTest {

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired
    private QueueRedisRepository queueRedisRepository;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Value("${queue.admitted-ttl-seconds}")
    private int admittedTtlSeconds;

    private static final String EVENT_ID = "test-event";

    @BeforeEach
    void setUp() {
        redisTemplate.execute((org.springframework.data.redis.core.RedisCallback<Void>) connection -> {
            connection.serverCommands().flushAll();
            return null;
        });
    }

    // === registerIdempotent 테스트 ===

    @Test
    @DisplayName("멱등 등록 - 같은 userId로 2회 등록하면 같은 queueKey를 반환한다")
    void registerIdempotent_sameUser_returnsExistingQueueKey() {
        // given
        double score = System.currentTimeMillis();

        // when — 첫 번째 등록
        List<Object> result1 = queueRedisRepository.registerIdempotent(
                EVENT_ID, "user-1", "qk-1", score, 600);

        // then — 신규 등록
        long isNew1 = toLong(result1.get(0));
        assertThat(isNew1).isEqualTo(1);

        // when — 같은 userId로 두 번째 등록
        List<Object> result2 = queueRedisRepository.registerIdempotent(
                EVENT_ID, "user-1", "qk-2", score + 100, 600);

        // then — 기존 반환 (멱등)
        long isNew2 = toLong(result2.get(0));
        String returnedKey = String.valueOf(result2.get(1));
        assertThat(isNew2).isEqualTo(0);
        assertThat(returnedKey).isEqualTo("qk-1");
    }

    @Test
    @DisplayName("멱등 등록 - 이미 admitted 상태면 차단(-1)을 반환한다")
    void registerIdempotent_admittedUser_returnsBlocked() {
        // given — 등록 후 입장
        queueRedisRepository.registerIdempotent(EVENT_ID, "user-1", "qk-1", 1.0, 600);
        queueRedisRepository.runAdmissionCycle(EVENT_ID, 10, 10, 300_000, 180_000, 100, 100);

        // when — 같은 userId로 재등록 시도
        List<Object> result = queueRedisRepository.registerIdempotent(
                EVENT_ID, "user-1", "qk-2", 2.0, 600);

        // then — admitted 상태라 차단
        long isNew = toLong(result.get(0));
        assertThat(isNew).isEqualTo(-1);
    }

    // === getStatusAtomic 테스트 ===

    @Test
    @DisplayName("상태 조회 - admitted 상태면 statusCode=1을 반환한다")
    void getStatusAtomic_admitted_returnsCode1() {
        // given
        queueRedisRepository.registerIdempotent(EVENT_ID, "user-1", "qk-1", 1.0, 600);
        queueRedisRepository.runAdmissionCycle(EVENT_ID, 10, 10, 300_000, 180_000, 100, 100);

        // when
        List<Long> result = queueRedisRepository.getStatusAtomic(EVENT_ID, "qk-1", 60);

        // then
        assertThat(result.get(0)).isEqualTo(1L);
    }

    @Test
    @DisplayName("상태 조회 - 대기 중이면 statusCode=2와 rank를 반환한다")
    void getStatusAtomic_waiting_returnsCode2WithRank() {
        // given
        queueRedisRepository.registerIdempotent(EVENT_ID, "user-1", "qk-1", 1.0, 600);
        queueRedisRepository.registerIdempotent(EVENT_ID, "user-2", "qk-2", 2.0, 600);

        // when
        List<Long> result = queueRedisRepository.getStatusAtomic(EVENT_ID, "qk-2", 60);

        // then
        assertThat(result.get(0)).isEqualTo(2L); // WAITING
        assertThat(result.get(1)).isEqualTo(1L); // rank=1 (0-indexed)
        assertThat(result.get(2)).isEqualTo(2L); // total=2
    }

    @Test
    @DisplayName("상태 조회 - 존재하지 않으면 statusCode=3을 반환한다")
    void getStatusAtomic_notFound_returnsCode3() {
        // when
        List<Long> result = queueRedisRepository.getStatusAtomic(EVENT_ID, "non-existent", 60);

        // then
        assertThat(result.get(0)).isEqualTo(3L);
    }

    // === runAdmissionCycle 테스트 ===

    @Test
    @DisplayName("입장 사이클 - 반환값 7개 + admitted members 문자열을 반환한다")
    void runAdmissionCycle_returnsExtendedResult() {
        // given — 3명 등록
        queueRedisRepository.registerIdempotent(EVENT_ID, "user-1", "qk-1", 1.0, 600);
        queueRedisRepository.registerIdempotent(EVENT_ID, "user-2", "qk-2", 2.0, 600);
        queueRedisRepository.registerIdempotent(EVENT_ID, "user-3", "qk-3", 3.0, 600);

        // when
        List<Object> result = queueRedisRepository.runAdmissionCycle(
                EVENT_ID, 10, 10, 300_000, 180_000, 100, 100);

        // then — 최소 7개 + admitted members
        assertThat(result.size()).isGreaterThanOrEqualTo(7);
        long admittedCount = toLong(result.get(2));
        assertThat(admittedCount).isEqualTo(3);

        // waitingSize=0, admittedSize=3
        assertThat(toLong(result.get(5))).isEqualTo(0);
        assertThat(toLong(result.get(6))).isEqualTo(3);

        // admitted members 문자열
        assertThat(result.size()).isEqualTo(10); // 7 + 3 members
    }

    // === tryLock / unlock 테스트 ===

    @Test
    @DisplayName("분산 락 - 획득/경쟁실패/해제/재획득이 정상 동작한다")
    void tryLock_unlock_lifecycle() {
        // when — 첫 번째 획득
        boolean locked1 = queueRedisRepository.tryLock(EVENT_ID, "owner-1", 5000);
        assertThat(locked1).isTrue();

        // when — 경쟁 실패
        boolean locked2 = queueRedisRepository.tryLock(EVENT_ID, "owner-2", 5000);
        assertThat(locked2).isFalse();

        // when — 잘못된 소유자 해제 실패
        boolean unlocked = queueRedisRepository.unlock(EVENT_ID, "owner-2");
        assertThat(unlocked).isFalse();

        // when — 올바른 소유자 해제
        boolean unlocked2 = queueRedisRepository.unlock(EVENT_ID, "owner-1");
        assertThat(unlocked2).isTrue();

        // when — 재획득
        boolean locked3 = queueRedisRepository.tryLock(EVENT_ID, "owner-2", 5000);
        assertThat(locked3).isTrue();
    }

    // === recordAdmissionRate 테스트 ===

    @Test
    @DisplayName("입장률 기록 - INCRBY 및 윈도우 합산이 정상 동작한다")
    void recordAdmissionRate_incrementsAndSums() {
        // given — 고정 epochSecond로 초 경계 crossing 방지
        long fixedEpoch = System.currentTimeMillis() / 1000;

        // when
        long total1 = queueRedisRepository.recordAdmissionRate(EVENT_ID, 5, fixedEpoch);
        long total2 = queueRedisRepository.recordAdmissionRate(EVENT_ID, 3, fixedEpoch);

        // then — 같은 초에 호출되므로 누적
        assertThat(total1).isEqualTo(5);
        assertThat(total2).isEqualTo(8);
    }

    // === 만료 admitted 정리 테스트 ===

    @Test
    @DisplayName("만료된 admitted 항목이 다음 사이클에서 제거된다")
    void runAdmissionCycle_expiresOldAdmittedEntries() {
        // given — admitted에 만료된 항목 직접 추가
        long ttlMs = admittedTtlSeconds * 1000L;
        long now = System.currentTimeMillis();
        long expiredTimestamp = now - ttlMs - 100_000;
        long validTimestamp = now - ttlMs + 200_000;

        redisTemplate.opsForZSet().add("queue:" + EVENT_ID + ":admitted", "expired-user", expiredTimestamp);
        redisTemplate.opsForZSet().add("queue:" + EVENT_ID + ":admitted", "valid-user", validTimestamp);

        // when
        List<Object> result = queueRedisRepository.runAdmissionCycle(
                EVENT_ID, 10, 10, ttlMs, 180_000, 100, 100);

        // then
        long expiredCount = toLong(result.get(0));
        assertThat(expiredCount).isEqualTo(1);
        assertThat(redisTemplate.opsForZSet().score("queue:" + EVENT_ID + ":admitted", "expired-user")).isNull();
        assertThat(redisTemplate.opsForZSet().score("queue:" + EVENT_ID + ":admitted", "valid-user")).isNotNull();
    }

    // === 토큰 버킷 제한 테스트 ===

    @Test
    @DisplayName("토큰이 소진되면 입장이 제한된다")
    void runAdmissionCycle_tokenBucketLimitsAdmission() {
        // given — 15명 등록
        for (int i = 1; i <= 15; i++) {
            queueRedisRepository.registerIdempotent(EVENT_ID, "user-" + i, "qk-" + i, (double) i, 600);
        }

        // when — capacity=10이므로 첫 사이클에서 최대 10명만 입장
        List<Object> result1 = queueRedisRepository.runAdmissionCycle(
                EVENT_ID, 10, 10, 300_000, 180_000, 100, 100);

        // then
        long admitted1 = toLong(result1.get(2));
        assertThat(admitted1).isEqualTo(10);
        assertThat(queueRedisRepository.getQueueSize(EVENT_ID)).isEqualTo(5);

        // when — 즉시 두 번째 사이클 (토큰 미리필)
        List<Object> result2 = queueRedisRepository.runAdmissionCycle(
                EVENT_ID, 10, 10, 300_000, 180_000, 100, 100);

        // then — 토큰이 거의 없으므로 입장 제한
        long admitted2 = toLong(result2.get(2));
        assertThat(admitted2).isLessThanOrEqualTo(1);
    }

    private long toLong(Object obj) {
        if (obj instanceof Long) return (Long) obj;
        if (obj instanceof Number) return ((Number) obj).longValue();
        if (obj instanceof String) return Long.parseLong((String) obj);
        return 0;
    }
}
