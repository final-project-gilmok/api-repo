package kr.gilmok.api.reservation.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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
class SeatLockRedisRepositoryIntegrationTest {

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired
    private SeatLockRedisRepository seatLockRedisRepository;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    private static final Long EVENT_ID = 1L;
    private static final Long SEAT_ID = 10L;
    private static final Long USER_ID = 100L;

    @BeforeEach
    void setUp() {
        redisTemplate.execute((org.springframework.data.redis.core.RedisCallback<Void>) connection -> {
            connection.serverCommands().flushAll();
            return null;
        });
    }

    @Test
    @DisplayName("잔여석 초기화 후 조회하면 설정한 수량을 반환한다")
    void initAvailable_and_getAvailable() {
        // when
        seatLockRedisRepository.initAvailable(EVENT_ID, SEAT_ID, 50);

        // then
        assertThat(seatLockRedisRepository.getAvailable(EVENT_ID, SEAT_ID)).isEqualTo(50);
    }

    @Test
    @DisplayName("좌석 잠금 성공 시 잔여석이 감소한다")
    void lock_success_decreasesAvailable() {
        // given
        seatLockRedisRepository.initAvailable(EVENT_ID, SEAT_ID, 10);

        // when
        boolean locked = seatLockRedisRepository.lock(EVENT_ID, SEAT_ID, USER_ID, 3, 300);

        // then
        assertThat(locked).isTrue();
        assertThat(seatLockRedisRepository.getAvailable(EVENT_ID, SEAT_ID)).isEqualTo(7);
    }

    @Test
    @DisplayName("잔여석 부족 시 잠금 실패한다")
    void lock_fails_whenNotEnoughAvailable() {
        // given
        seatLockRedisRepository.initAvailable(EVENT_ID, SEAT_ID, 2);

        // when
        boolean locked = seatLockRedisRepository.lock(EVENT_ID, SEAT_ID, USER_ID, 3, 300);

        // then
        assertThat(locked).isFalse();
        assertThat(seatLockRedisRepository.getAvailable(EVENT_ID, SEAT_ID)).isEqualTo(2);
    }

    @Test
    @DisplayName("잠금 해제 시 잔여석이 복구된다")
    void unlockAndRestore_restoresAvailable() {
        // given
        seatLockRedisRepository.initAvailable(EVENT_ID, SEAT_ID, 10);
        seatLockRedisRepository.lock(EVENT_ID, SEAT_ID, USER_ID, 3, 300);

        // when
        long restored = seatLockRedisRepository.unlockAndRestore(EVENT_ID, SEAT_ID, USER_ID);

        // then
        assertThat(restored).isEqualTo(3);
        assertThat(seatLockRedisRepository.getAvailable(EVENT_ID, SEAT_ID)).isEqualTo(10);
    }

    @Test
    @DisplayName("잠금이 없는 상태에서 해제 시 0을 반환한다")
    void unlockAndRestore_noLock_returnsZero() {
        // given
        seatLockRedisRepository.initAvailable(EVENT_ID, SEAT_ID, 10);

        // when
        long restored = seatLockRedisRepository.unlockAndRestore(EVENT_ID, SEAT_ID, USER_ID);

        // then
        assertThat(restored).isEqualTo(0);
        assertThat(seatLockRedisRepository.getAvailable(EVENT_ID, SEAT_ID)).isEqualTo(10);
    }

    @Test
    @DisplayName("동시에 여러 사용자가 잠금 요청 시 잔여석이 정확하게 감소한다")
    void lock_multipleUsers_correctAvailable() {
        // given
        seatLockRedisRepository.initAvailable(EVENT_ID, SEAT_ID, 10);

        // when
        boolean locked1 = seatLockRedisRepository.lock(EVENT_ID, SEAT_ID, 1L, 3, 300);
        boolean locked2 = seatLockRedisRepository.lock(EVENT_ID, SEAT_ID, 2L, 4, 300);
        boolean locked3 = seatLockRedisRepository.lock(EVENT_ID, SEAT_ID, 3L, 5, 300); // 남은 3석, 5개 요청 → 실패

        // then
        assertThat(locked1).isTrue();
        assertThat(locked2).isTrue();
        assertThat(locked3).isFalse();
        assertThat(seatLockRedisRepository.getAvailable(EVENT_ID, SEAT_ID)).isEqualTo(3);
    }
}
