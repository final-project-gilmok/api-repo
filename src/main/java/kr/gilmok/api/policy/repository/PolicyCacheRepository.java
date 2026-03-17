package kr.gilmok.api.policy.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import kr.gilmok.api.policy.dto.PolicyCacheDto;
import kr.gilmok.api.policy.entity.Policy;
import kr.gilmok.api.policy.repository.PolicyRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Slf4j
@Repository
public class PolicyCacheRepository {

    private static final String KEY_PREFIX = "policy:";

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;
    private final PolicyRepository policyRepository;
    private final long ttlSecondsPositive;
    private final long ttlSecondsNegative;

    public PolicyCacheRepository(
            RedisTemplate<String, String> redisTemplate,
            ObjectMapper objectMapper,
            PolicyRepository policyRepository,
            @Value("${policy.cache.ttl-hours:1}") long ttlHours,
            @Value("${policy.cache.negative-ttl-minutes:5}") long negativeTtlMinutes) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.policyRepository = policyRepository;
        this.ttlSecondsPositive = ttlHours * 3600L;
        this.ttlSecondsNegative = negativeTtlMinutes * 60L;
    }

    @Retryable(
            retryFor = {DataAccessException.class},
            maxAttempts = 2,
            backoff = @Backoff(delay = 200)
    )
    public Optional<PolicyCacheDto> find(Long eventId) {
        String key = KEY_PREFIX + eventId;
        String json = redisTemplate.opsForValue().get(key);
        if (json == null || json.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(json, PolicyCacheDto.class));
        } catch (JsonProcessingException e) {
            log.warn("Policy cache deserialize failed: eventId={}, key={}", eventId, key, e);
            return Optional.empty();
        }
    }

    @Recover
    public Optional<PolicyCacheDto> recoverFind(DataAccessException e, Long eventId) {
        log.warn("Policy cache retry exhausted, falling back to DB: eventId={}", eventId);
        return policyRepository.findByEventId(eventId)
                .map(PolicyCacheDto::from);
    }

/*정책 캐시 저장. exists=true 이면 positive TTL(기본 1시간), false 이면 negative TTL(기본 5분).*/
    public void save(Long eventId, PolicyCacheDto dto) {
        String key = KEY_PREFIX + eventId;
        try {
            String json = objectMapper.writeValueAsString(dto);
            long ttlSeconds = dto.exists() ? ttlSecondsPositive : ttlSecondsNegative;
            redisTemplate.opsForValue().set(key, json, ttlSeconds, TimeUnit.SECONDS);
        } catch (JsonProcessingException e) {
            log.warn("Policy cache serialize failed: eventId={}, key={}", eventId, key, e);
        }
    }

    /** 해당 eventId의 정책 캐시를 삭제한다. 이벤트 close 시 호출. */
    public void evict(Long eventId) {
        String key = KEY_PREFIX + eventId;
        Boolean removed = redisTemplate.delete(key);
        if (Boolean.TRUE.equals(removed)) {
            log.debug("Policy cache evicted: eventId={}", eventId);
        }
    }
}
