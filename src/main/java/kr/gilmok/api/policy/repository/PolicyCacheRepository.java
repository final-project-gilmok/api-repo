package kr.gilmok.api.policy.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import kr.gilmok.api.policy.dto.PolicyCacheDto;
import kr.gilmok.api.policy.entity.Policy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.*;

@Slf4j
@Repository
public class PolicyCacheRepository {

    private static final String KEY_PREFIX = "policy:";

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;
    private final PolicyRepository policyRepository;

    private final long ttlSecondsPositive;
    private final long ttlSecondsNegative;
    private final long joinTimeoutMs;

    private final Semaphore dbFallbackSemaphore;

    private final Cache<Long, PolicyCacheDto> localCache;

    private final ConcurrentHashMap<Long, CompletableFuture<Optional<PolicyCacheDto>>> inFlight =
            new ConcurrentHashMap<>();

    public PolicyCacheRepository(
            RedisTemplate<String, String> redisTemplate,
            ObjectMapper objectMapper,
            PolicyRepository policyRepository,
            @Value("${policy.cache.ttl-hours:1}") long ttlHours,
            @Value("${policy.cache.negative-ttl-minutes:5}") long negativeTtlMinutes,
            @Value("${policy.local-cache.ttl-seconds:60}") long localCacheTtlSeconds,
            @Value("${policy.local-cache.maximum-size:1000}") long localCacheMaximumSize,
            @Value("${policy.db-fallback.join-timeout-ms:500}") long joinTimeoutMs,
            @Value("${policy.db-fallback.max-concurrency:5}") int dbFallbackMaxConcurrency
    ) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.policyRepository = policyRepository;
        this.ttlSecondsPositive = ttlHours * 3600L;
        this.ttlSecondsNegative = negativeTtlMinutes * 60L;
        this.joinTimeoutMs = joinTimeoutMs;
        this.dbFallbackSemaphore = new Semaphore(dbFallbackMaxConcurrency);

        this.localCache = Caffeine.newBuilder()
                .maximumSize(localCacheMaximumSize)
                .expireAfterWrite(Duration.ofSeconds(localCacheTtlSeconds))
                .build();
    }

    /**
     * 조회 순서
     * 1) Redis
     * 2) Local cache
     * 3) Single-flight + DB fallback
     */
    public Optional<PolicyCacheDto> find(Long eventId) {
        if (eventId == null) {
            return Optional.empty();
        }

        // 1. Redis
        Optional<PolicyCacheDto> redisResult = findFromRedis(eventId);
        if (redisResult.isPresent()) {
            localCache.put(eventId, redisResult.get());
            return redisResult;
        }

        // 2. Local cache
        PolicyCacheDto localHit = localCache.getIfPresent(eventId);
        if (localHit != null) {
            log.debug("Policy local cache hit: eventId={}", eventId);
            return Optional.of(localHit);
        }

        // 3. DB fallback with single-flight
        return findFromDbSingleFlight(eventId);
    }

    private Optional<PolicyCacheDto> findFromRedis(Long eventId) {
        String key = KEY_PREFIX + eventId;

        try {
            String json = redisTemplate.opsForValue().get(key);
            if (json == null || json.isBlank()) {
                return Optional.empty();
            }

            try {
                PolicyCacheDto dto = objectMapper.readValue(json, PolicyCacheDto.class);
                return Optional.of(dto);
            } catch (JsonProcessingException e) {
                log.warn("Policy cache deserialize failed: eventId={}, key={}", eventId, key, e);
                return Optional.empty();
            }
        } catch (DataAccessException e) {
            log.warn("Policy cache read failed, fallback to local/DB: eventId={}, key={}", eventId, key, e);
            return Optional.empty();
        }
    }

    private Optional<PolicyCacheDto> findFromDbSingleFlight(Long eventId) {
        CompletableFuture<Optional<PolicyCacheDto>> newFuture = new CompletableFuture<>();
        CompletableFuture<Optional<PolicyCacheDto>> existing = inFlight.putIfAbsent(eventId, newFuture);

        if (existing != null) {
            log.debug("Join in-flight policy fetch: eventId={}", eventId);
            try {
                return existing.get(joinTimeoutMs, TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                log.warn("In-flight join timed out: eventId={}", eventId);
                return Optional.empty();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("In-flight join interrupted: eventId={}", eventId, e);
                return Optional.empty();
            } catch (ExecutionException e) {
                Throwable cause = e.getCause() != null ? e.getCause() : e;
                log.warn("Joined in-flight fetch failed: eventId={}", eventId, cause);
                return Optional.empty();
            }
        }

        try {
            Optional<PolicyCacheDto> result = fallbackToDb(eventId);
            newFuture.complete(result);
            return result;
        } catch (Exception e) {
            newFuture.completeExceptionally(e);
            log.warn("Policy DB fallback failed: eventId={}", eventId, e);
            return Optional.empty();
        } finally {
            inFlight.remove(eventId, newFuture);
        }
    }

    private Optional<PolicyCacheDto> fallbackToDb(Long eventId) {
        boolean acquired = dbFallbackSemaphore.tryAcquire();
        if (!acquired) {
            log.warn("Policy DB fallback rejected by semaphore: eventId={}", eventId);
            return Optional.empty();
        }

        PolicyCacheDto dto;
        try {
            log.info("Policy fallback to DB: eventId={}", eventId);

            Optional<Policy> policyOpt = policyRepository.findByEventId(eventId);

            dto = policyOpt
                    .map(PolicyCacheDto::from)
                    .orElseGet(() -> PolicyCacheDto.negative(eventId));

            localCache.put(eventId, dto);
        } finally {
            dbFallbackSemaphore.release();
        }

        save(eventId, dto);
        return Optional.of(dto);
    }

    /**
     * Redis 저장 + local cache 동기화
     */
    public void save(Long eventId, PolicyCacheDto dto) {
        if (eventId == null || dto == null) {
            return;
        }

        String key = KEY_PREFIX + eventId;

        try {
            String json = objectMapper.writeValueAsString(dto);
            long ttlSeconds = dto.exists() ? ttlSecondsPositive : ttlSecondsNegative;
            redisTemplate.opsForValue().set(key, json, ttlSeconds, TimeUnit.SECONDS);
        } catch (JsonProcessingException e) {
            log.warn("Policy cache serialize failed: eventId={}, key={}", eventId, key, e);
        } catch (DataAccessException e) {
            log.warn("Policy cache write failed: eventId={}, key={}", eventId, key, e);
        }

        localCache.put(eventId, dto);
    }

    public void evict(Long eventId) {
        if (eventId == null) {
            return;
        }

        String key = KEY_PREFIX + eventId;

        try {
            Boolean removed = redisTemplate.delete(key);
            if (Boolean.TRUE.equals(removed)) {
                log.debug("Policy cache evicted from Redis: eventId={}", eventId);
            }
        } catch (DataAccessException e) {
            log.warn("Policy cache evict failed from Redis: eventId={}, key={}", eventId, key, e);
        }

        localCache.invalidate(eventId);
        inFlight.remove(eventId);
    }


}