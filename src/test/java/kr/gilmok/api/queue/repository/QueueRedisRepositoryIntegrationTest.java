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

    @Test
    @DisplayName("등록 후 rank를 조회하면 0을 반환한다")
    void register_and_getRank() {
        // given
        String queueKey = "user-1";
        double score = System.currentTimeMillis();

        // when
        boolean registered = queueRedisRepository.register(EVENT_ID, "session-1", queueKey, score);
        Long rank = queueRedisRepository.getRank(EVENT_ID, queueKey);

        // then
        assertThat(registered).isTrue();
        assertThat(rank).isEqualTo(0L);
    }

    @Test
    @DisplayName("admitFromHead 호출 시 대기열에서 admitted Sorted Set으로 이동하고 score가 timestamp이다")
    void admitFromHead_movesToSortedSet() {
        // given
        String queueKey = "user-1";
        queueRedisRepository.register(EVENT_ID, "session-1", queueKey, 1.0);

        long beforeAdmit = System.currentTimeMillis();

        // when
        queueRedisRepository.admitFromHead(EVENT_ID, 1);

        long afterAdmit = System.currentTimeMillis();

        // then — 대기열에서 제거됨
        assertThat(queueRedisRepository.getRank(EVENT_ID, queueKey)).isNull();

        // then — admitted Sorted Set에 존재하며 score가 현재 시간 범위 안에 있다
        Double admittedScore = redisTemplate.opsForZSet()
                .score("queue:" + EVENT_ID + ":admitted", queueKey);
        assertThat(admittedScore).isNotNull();
        assertThat(admittedScore.longValue()).isBetween(beforeAdmit, afterAdmit);
    }

    @Test
    @DisplayName("admitted 후 isAdmitted가 true를 반환한다")
    void isAdmitted_returnsTrueAfterAdmit() {
        // given
        String queueKey = "user-1";
        queueRedisRepository.register(EVENT_ID, "session-1", queueKey, 1.0);

        // when
        queueRedisRepository.admitFromHead(EVENT_ID, 1);

        // then
        assertThat(queueRedisRepository.isAdmitted(EVENT_ID, queueKey)).isTrue();
        assertThat(queueRedisRepository.isAdmitted(EVENT_ID, "non-existent")).isFalse();
    }

    @Test
    @DisplayName("만료된 admitted 항목만 삭제되고 유효한 항목은 남는다")
    void removeExpiredAdmitted_removesOldEntries() {
        // given — 두 사용자를 admitted에 직접 추가 (score = timestamp)
        long ttlMs = admittedTtlSeconds * 1000L;
        long now = System.currentTimeMillis();
        long expiredTimestamp = now - ttlMs - 100_000;  // TTL + 100초 전 (만료됨)
        long validTimestamp = now - ttlMs + 200_000;    // TTL - 200초 전 (유효함)

        redisTemplate.opsForZSet().add("queue:" + EVENT_ID + ":admitted", "expired-user", expiredTimestamp);
        redisTemplate.opsForZSet().add("queue:" + EVENT_ID + ":admitted", "valid-user", validTimestamp);

        // when — cutoff = now - TTL
        long cutoffMs = now - ttlMs;
        long removed = queueRedisRepository.removeExpiredAdmitted(EVENT_ID, cutoffMs);

        // then
        assertThat(removed).isEqualTo(1);
        assertThat(redisTemplate.opsForZSet().score("queue:" + EVENT_ID + ":admitted", "expired-user")).isNull();
        assertThat(redisTemplate.opsForZSet().score("queue:" + EVENT_ID + ":admitted", "valid-user")).isNotNull();
    }

    @Test
    @DisplayName("token-bucket.lua가 실제 Redis에서 토큰 소비 및 리필 동작을 수행한다")
    void consumeTokens_luaScriptWorks() {
        // given — maxTokens=10이므로 첫 호출 시 최대 10개 소비 가능
        int maxTokens = 10;

        // when — 첫 호출: 10개 전부 요청하여 토큰 소진
        long consumed1 = queueRedisRepository.consumeTokens(EVENT_ID, maxTokens, 10);

        // then — 10개 소비됨
        assertThat(consumed1).isEqualTo(10);

        // when — 즉시 두 번째 호출: 토큰 고갈 상태에서 5개 요청
        // (리필 간격 내이므로 0~1개 정도만 리필될 수 있음)
        long consumed2 = queueRedisRepository.consumeTokens(EVENT_ID, maxTokens, 5);

        // then — 토큰이 거의 없으므로 소비량이 매우 적다 (리필에 의한 미세 차이 허용)
        assertThat(consumed2).isLessThanOrEqualTo(1);

        // when — 세 번째 호출: 여전히 고갈 상태
        long consumed3 = queueRedisRepository.consumeTokens(EVENT_ID, maxTokens, 3);

        // then — 토큰이 거의 없다
        assertThat(consumed3).isLessThanOrEqualTo(1);
    }
}
