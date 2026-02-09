package kr.gilmok.api.queue.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.DefaultTypedTuple;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.List;
import java.util.Set;

@Repository
@RequiredArgsConstructor
public class QueueRedisRepository {

    private final RedisTemplate<String, String> redisTemplate;
    private final DefaultRedisScript<Long> tokenBucketScript;

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

    public void admitFromHead(String eventId, long count) {
        if (count <= 0) {
            return;
        }
        Set<ZSetOperations.TypedTuple<String>> popped = redisTemplate.opsForZSet()
                .popMin(queueKey(eventId), count);
        if (popped != null && !popped.isEmpty()) {
            long now = System.currentTimeMillis();
            Set<ZSetOperations.TypedTuple<String>> admittedTuples = new java.util.LinkedHashSet<>();
            for (ZSetOperations.TypedTuple<String> tuple : popped) {
                admittedTuples.add(new DefaultTypedTuple<>(tuple.getValue(), (double) now));
            }
            redisTemplate.opsForZSet().add(admittedKey(eventId), admittedTuples);
        }
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
