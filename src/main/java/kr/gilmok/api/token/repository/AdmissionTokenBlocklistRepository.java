package kr.gilmok.api.token.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.util.concurrent.TimeUnit;

/**
 * Admission Token One-Time 사용 기록 저장소.
 * 예약 확정 후 admission token의 jti를 Redis에 기록하여 재사용을 방지한다.
 * 키 형식: {@code admission:used:{jti}}
 */
@Repository
@RequiredArgsConstructor
public class AdmissionTokenBlocklistRepository {

    private static final String KEY_PREFIX = "admission:used:";

    private final RedisTemplate<String, String> redisTemplate;

    /**
     * 사용된 admission token의 jti를 기록한다.
     * 
     * @param jti        토큰의 JWT ID
     * @param ttlSeconds 보존 기간 (토큰 남은 유효시간, 초 단위)
     */
    public void markAsUsed(String jti, long ttlSeconds) {
        if (ttlSeconds <= 0)
            return;
        redisTemplate.opsForValue().set(
                KEY_PREFIX + jti,
                "1",
                ttlSeconds,
                TimeUnit.SECONDS);
    }

    /**
     * jti가 이미 사용된 토큰인지 확인한다.
     * 
     * @return 사용된 토큰이면 true
     */
    public boolean isUsed(String jti) {
        return redisTemplate.hasKey(KEY_PREFIX + jti);
    }
}
