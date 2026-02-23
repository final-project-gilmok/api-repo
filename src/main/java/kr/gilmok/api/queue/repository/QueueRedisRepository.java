package kr.gilmok.api.queue.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Repository
@RequiredArgsConstructor
public class QueueRedisRepository {

    private final RedisTemplate<String, String> redisTemplate;
    private final DefaultRedisScript<Long> tokenBucketScript;
    private final DefaultRedisScript<Long> admitFromHeadScript;
    private final DefaultRedisScript<Long> cleanupGracePeriodScript;

    private String queueKey(String eventId) {
        return "queue:" + eventId;
    }

    private String admittedKey(String eventId) {
        return "queue:" + eventId + ":admitted";
    }

    private String tokenBucketKey(String eventId) {
        return "queue:" + eventId + ":token-bucket";
    }

    public boolean register(String eventId, String queueKey, double score) {
        Boolean added = redisTemplate.opsForZSet().addIfAbsent(queueKey(eventId), queueKey, score);
        return Boolean.TRUE.equals(added);
    }

    public Long getRank(String eventId, String queueKey) {
        return redisTemplate.opsForZSet().rank(queueKey(eventId), queueKey);
    }

    public long getQueueSize(String eventId) {
        Long size = redisTemplate.opsForZSet().zCard(queueKey(eventId));
        return size != null ? size : 0;
    }

    public boolean isAdmitted(String eventId, String queueKey) {
        Double score = redisTemplate.opsForZSet().score(admittedKey(eventId), queueKey);
        return score != null;
    }

    public long admitFromHead(String eventId, long count) {
        if (count <= 0) {
            return 0;
        }
        Long admitted = redisTemplate.execute(
                admitFromHeadScript,
                Arrays.asList(queueKey(eventId), admittedKey(eventId)),
                String.valueOf(count),
                String.valueOf(System.currentTimeMillis())
        );
        return admitted != null ? admitted : 0;
    }

    public long removeExpiredAdmitted(String eventId, long cutoffMs) {
        Long removed = redisTemplate.opsForZSet().removeRangeByScore(admittedKey(eventId), 0, cutoffMs);
        return removed != null ? removed : 0;
    }

    public long getAdmittedCount(String eventId) {
        Long size = redisTemplate.opsForZSet().zCard(admittedKey(eventId));
        return size != null ? size : 0;
    }

    // === Admission log (이동평균 ETA) ===

    private String admissionLogKey(String eventId) {
        return "queue:" + eventId + ":admission-log";
    }

    public void recordAdmission(String eventId, long count) {
        String key = admissionLogKey(eventId);
        long ts = System.currentTimeMillis();
        redisTemplate.opsForZSet().add(key, ts + ":" + count, ts);
        long cutoff = ts - TimeUnit.SECONDS.toMillis(300);
        redisTemplate.opsForZSet().removeRangeByScore(key, 0, cutoff);
        redisTemplate.expire(key, 300, TimeUnit.SECONDS);
    }

    public Double getMovingAverageRps(String eventId, long lookbackMs) {
        String key = admissionLogKey(eventId);
        long now = System.currentTimeMillis();
        long from = now - lookbackMs;
        Set<String> entries = redisTemplate.opsForZSet().rangeByScore(key, from, now);
        if (entries == null || entries.isEmpty()) {
            return null;
        }
        long totalCount = 0;
        for (String entry : entries) {
            String[] parts = entry.split(":");
            if (parts.length == 2) {
                totalCount += Long.parseLong(parts[1]);
            }
        }
        double seconds = lookbackMs / 1000.0;
        return totalCount / seconds;
    }

    // === Heartbeat & Grace Period ===

    private String heartbeatsKey(String eventId) {
        return "queue:" + eventId + ":heartbeats";
    }

    public void updateHeartbeat(String eventId, String queueKey) {
        String key = heartbeatsKey(eventId);
        long now = System.currentTimeMillis();
        redisTemplate.opsForZSet().add(key, queueKey, now);
        redisTemplate.expire(key, 600, TimeUnit.SECONDS);
    }

    public long cleanupGracePeriod(String eventId, long gracePeriodMs) {
        Long removed = redisTemplate.execute(
                cleanupGracePeriodScript,
                Arrays.asList(queueKey(eventId), heartbeatsKey(eventId)),
                String.valueOf(gracePeriodMs),
                String.valueOf(System.currentTimeMillis())
        );
        return removed != null ? removed : 0;
    }

    public long consumeTokens(String eventId, long maxTokens, long requested) {
        Long consumed = redisTemplate.execute(
                tokenBucketScript,
                Collections.singletonList(tokenBucketKey(eventId)),
                String.valueOf(maxTokens),
                String.valueOf(System.currentTimeMillis()),
                String.valueOf(requested)
        );
        return consumed != null ? consumed : 0;
    }
}
