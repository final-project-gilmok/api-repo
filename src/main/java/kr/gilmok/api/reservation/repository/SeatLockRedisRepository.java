package kr.gilmok.api.reservation.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Repository
@RequiredArgsConstructor
public class SeatLockRedisRepository {

    private final RedisTemplate<String, String> redisTemplate;
    private final DefaultRedisScript<Long> seatLockScript;
    private final DefaultRedisScript<Long> seatUnlockRestoreScript;

    private String availableKey(Long eventId, Long seatId) {
        return "seat-available:" + eventId + ":" + seatId;
    }

    private String lockKey(Long eventId, Long seatId, Long userId) {
        return "seat-lock:" + eventId + ":" + seatId + ":" + userId;
    }

    public boolean lock(Long eventId, Long seatId, Long userId, int quantity, int ttlSeconds) {
        Long result = redisTemplate.execute(
                seatLockScript,
                Arrays.asList(availableKey(eventId, seatId), lockKey(eventId, seatId, userId)),
                String.valueOf(quantity),
                String.valueOf(ttlSeconds)
        );
        return result != null && result == 1;
    }

    public long unlockAndRestore(Long eventId, Long seatId, Long userId) {
        Long restored = redisTemplate.execute(
                seatUnlockRestoreScript,
                Arrays.asList(availableKey(eventId, seatId), lockKey(eventId, seatId, userId))
        );
        return restored != null ? restored : 0;
    }

    public void initAvailable(Long eventId, Long seatId, int availableCount) {
        redisTemplate.opsForValue().set(availableKey(eventId, seatId), String.valueOf(availableCount));
    }

    public int getAvailable(Long eventId, Long seatId) {
        String value = redisTemplate.opsForValue().get(availableKey(eventId, seatId));
        return parseAvailable(value);
    }

    public Map<Long, Integer> getAvailableBulk(Long eventId, List<Long> seatIds) {
        if (seatIds == null || seatIds.isEmpty()) {
            return new HashMap<>();
        }

        List<String> keys = seatIds.stream()
                .map(seatId -> availableKey(eventId, seatId))
                .toList();

        List<String> values = redisTemplate.opsForValue().multiGet(keys);

        Map<Long, Integer> result = new HashMap<>(seatIds.size());
        for (int i = 0; i < seatIds.size(); i++) {
            String value = (values != null && i < values.size()) ? values.get(i) : null;
            result.put(seatIds.get(i), parseAvailable(value));
        }
        return result;
    }

    private int parseAvailable(String value) {
        if (value == null) return 0;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public void deleteAvailable(Long eventId, Long seatId) {
        redisTemplate.delete(availableKey(eventId, seatId));
    }
}
