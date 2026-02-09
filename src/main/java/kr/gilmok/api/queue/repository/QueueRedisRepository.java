package kr.gilmok.api.queue.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

import java.util.Arrays;
import java.util.Collections;

@Repository
@RequiredArgsConstructor
public class QueueRedisRepository {

    private final RedisTemplate<String, String> redisTemplate;
    private final DefaultRedisScript<Long> tokenBucketScript;
    private final DefaultRedisScript<Long> admitFromHeadScript;

    private String queueKey(String eventId) {
        return "queue:" + eventId;
    }

    private String sessionsKey(String eventId) {
        return "queue:" + eventId + ":sessions";
    }

    private String admittedKey(String eventId) {
        return "queue:" + eventId + ":admitted";
    }

    private String tokenBucketKey(String eventId) {
        return "queue:" + eventId + ":token-bucket";
    }

    public String findQueueKeyBySession(String eventId, String sessionKey) {
        Object value = redisTemplate.opsForHash().get(sessionsKey(eventId), sessionKey);
        return value != null ? value.toString() : null;
    }

    public boolean register(String eventId, String sessionKey, String queueKey, double score) {
        Boolean added = redisTemplate.opsForZSet().addIfAbsent(queueKey(eventId), queueKey, score);
        if (Boolean.TRUE.equals(added)) {
            redisTemplate.opsForHash().put(sessionsKey(eventId), sessionKey, queueKey);
            return true;
        }
        return false;
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
