package kr.gilmok.api.queue.repository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Repository
@RequiredArgsConstructor
public class QueueRedisRepository {

    private final RedisTemplate<String, String> redisTemplate;
    private final DefaultRedisScript<List> fastAdmissionCycleScript;
    private final DefaultRedisScript<List> queueStatusScript;
    private final DefaultRedisScript<List> registerIdempotentScript;
    private final DefaultRedisScript<List> admissionRateScript;
    private final DefaultRedisScript<Long> unlockScript;

    // === Key helpers ===

    private String queueKey(String eventId) {
        return "queue:" + eventId;
    }

    private String admittedKey(String eventId) {
        return "queue:" + eventId + ":admitted";
    }

    private String tokenBucketKey(String eventId) {
        return "queue:" + eventId + ":token-bucket";
    }

    private String heartbeatsKey(String eventId) {
        return "queue:" + eventId + ":heartbeats";
    }

    private String sessionKey(String eventId, String queueKey) {
        return "queue:" + eventId + ":session:" + queueKey;
    }

    private String admitRateKey(String eventId) {
        return "queue:" + eventId + ":admit-rate";
    }

    private String userIndexKey(String eventId) {
        return "queue:" + eventId + ":user-index";
    }

    private String lockKey(String eventId) {
        return "lock:queue:admission:" + eventId;
    }

    // === Distributed Lock ===

    public boolean tryLock(String eventId, String lockValue, long ttlMs) {
        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(lockKey(eventId), lockValue, ttlMs, TimeUnit.MILLISECONDS);
        return Boolean.TRUE.equals(acquired);
    }

    public boolean unlock(String eventId, String lockValue) {
        Long result = redisTemplate.execute(
                unlockScript,
                List.of(lockKey(eventId)),
                lockValue
        );
        return result != null && result == 1L;
    }

    // === Atomic Status (1 Redis call) ===

    @SuppressWarnings("unchecked")
    public List<Long> getStatusAtomic(String eventId, String queueKeyVal, int windowSeconds) {
        List<Long> result = redisTemplate.execute(
                queueStatusScript,
                Arrays.asList(
                        queueKey(eventId),
                        admittedKey(eventId),
                        heartbeatsKey(eventId),
                        sessionKey(eventId, queueKeyVal),
                        admitRateKey(eventId)
                ),
                queueKeyVal,
                String.valueOf(System.currentTimeMillis()),
                String.valueOf(windowSeconds)
        );
        if (result == null) {
            log.error("Redis script returned null: queueStatusScript, eventId={}", eventId);
            throw new IllegalStateException("Redis script returned null: queueStatusScript");
        }
        return result;
    }

    // === Idempotent Registration (1 Redis call) ===

    @SuppressWarnings("unchecked")
    public List<Object> registerIdempotent(String eventId, String userId,
                                           String newQueueKey, double score, int sessionTtlSeconds) {
        List<Object> result = redisTemplate.execute(
                registerIdempotentScript,
                Arrays.asList(
                        queueKey(eventId),
                        admittedKey(eventId),
                        userIndexKey(eventId),
                        heartbeatsKey(eventId),
                        sessionKey(eventId, newQueueKey)
                ),
                userId,
                newQueueKey,
                String.valueOf((long) score),
                String.valueOf(System.currentTimeMillis()),
                String.valueOf(sessionTtlSeconds),
                eventId
        );
        if (result == null) {
            log.error("Redis script returned null: registerIdempotentScript, eventId={}", eventId);
            throw new IllegalStateException("Redis script returned null: registerIdempotentScript");
        }
        return result;
    }

    // === Admission Rate (1 Redis call) ===

    @SuppressWarnings("unchecked")
    public long recordAdmissionRate(String eventId, long count, long epochSecond) {
        List<Long> result = redisTemplate.execute(
                admissionRateScript,
                List.of(admitRateKey(eventId)),
                String.valueOf(epochSecond),
                String.valueOf(count),
                String.valueOf(60),
                String.valueOf(120)
        );
        if (result == null || result.isEmpty()) {
            log.error("Redis script returned null: admissionRateScript, eventId={}", eventId);
            throw new IllegalStateException("Redis script returned null: admissionRateScript");
        }
        return result.get(0);
    }

    // === Session Updates (pipeline) ===

    public void updateSessionsToAdmitted(String eventId, List<String> queueKeys, int sessionTtlSeconds) {
        redisTemplate.executePipelined((org.springframework.data.redis.core.RedisCallback<Void>) connection -> {
            byte[] stateField = "state".getBytes();
            byte[] admittedValue = "ADMITTED".getBytes();
            byte[] lastSeenField = "lastSeenAt".getBytes();
            byte[] nowBytes = String.valueOf(System.currentTimeMillis()).getBytes();

            for (String qk : queueKeys) {
                byte[] key = sessionKey(eventId, qk).getBytes();
                connection.hashCommands().hSet(key, stateField, admittedValue);
                connection.hashCommands().hSet(key, lastSeenField, nowBytes);
                connection.commands().expire(key, sessionTtlSeconds);
            }
            return null;
        });
    }

    // === Fast Admission Cycle (single EVAL) ===

    @SuppressWarnings("unchecked")
    public List<Object> runAdmissionCycle(String eventId, long rate, long capacity,
                                          long admittedTtlMs, long graceMs,
                                          int cleanupBatch, int expireBatch) {
        List<Object> result = redisTemplate.execute(
                fastAdmissionCycleScript,
                Arrays.asList(queueKey(eventId), admittedKey(eventId), heartbeatsKey(eventId), tokenBucketKey(eventId)),
                String.valueOf(rate),
                String.valueOf(capacity),
                String.valueOf(System.currentTimeMillis()),
                String.valueOf(admittedTtlMs),
                String.valueOf(graceMs),
                String.valueOf(cleanupBatch),
                String.valueOf(expireBatch)
        );
        if (result == null) {
            log.error("Redis script returned null: fastAdmissionCycleScript, eventId={}", eventId);
            throw new IllegalStateException("Redis script returned null: fastAdmissionCycleScript");
        }
        return result;
    }

    // === Heartbeat (ZADD only, no EXPIRE) ===

    public void updateHeartbeat(String eventId, String queueKey) {
        String key = heartbeatsKey(eventId);
        long now = System.currentTimeMillis();
        redisTemplate.opsForZSet().add(key, queueKey, now);
    }

    // === Basic registration (kept for backward compat, prefer registerIdempotent) ===

    public boolean register(String eventId, String queueKey, double score) {
        Boolean added = redisTemplate.opsForZSet().addIfAbsent(queueKey(eventId), queueKey, score);
        return Boolean.TRUE.equals(added);
    }

    // === Admission check (used by ReservationService) ===

    public boolean isAdmitted(String eventId, String queueKey) {
        Double score = redisTemplate.opsForZSet().score(admittedKey(eventId), queueKey);
        return score != null;
    }

    // === Size queries (init/healthcheck only) ===

    public long getQueueSize(String eventId) {
        Long size = redisTemplate.opsForZSet().zCard(queueKey(eventId));
        return size != null ? size : 0;
    }

    public long getAdmittedCount(String eventId) {
        Long size = redisTemplate.opsForZSet().zCard(admittedKey(eventId));
        return size != null ? size : 0;
    }
}
