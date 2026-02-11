package kr.gilmok.api.policy.service;

import kr.gilmok.api.event.exception.EventErrorCode;
import kr.gilmok.api.event.repository.EventRepository;
import kr.gilmok.api.policy.dto.AiRecommendationResponse;
import kr.gilmok.api.policy.dto.MetricsResponse;
import kr.gilmok.api.policy.dto.PolicyCacheDto;
import kr.gilmok.api.policy.dto.PolicyResponse;
import kr.gilmok.api.policy.dto.PolicyUpdateRequest;
import kr.gilmok.api.policy.entity.Policy;
import kr.gilmok.api.policy.entity.PolicyHistory;
import kr.gilmok.api.policy.exception.PolicyErrorCode;
import kr.gilmok.api.policy.repository.PolicyCacheRepository;
import kr.gilmok.api.policy.repository.PolicyHistoryRepository;
import kr.gilmok.api.policy.repository.PolicyRepository;
import kr.gilmok.common.exception.CustomException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import jakarta.persistence.OptimisticLockException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

@Slf4j
@Service
public class PolicyService {

    private final PolicyRepository policyRepository;
    private final PolicyHistoryRepository historyRepository;
    private final PolicyCacheRepository policyCacheRepository;
    private final EventRepository eventRepository;
    private final RabbitTemplate rabbitTemplate; // 실시간 반영 (Rabbit 비활성화 시 null)

    public PolicyService(PolicyRepository policyRepository,
                         PolicyHistoryRepository historyRepository,
                         PolicyCacheRepository policyCacheRepository,
                         EventRepository eventRepository,
                         @Autowired(required = false) RabbitTemplate rabbitTemplate) {
        this.policyRepository = policyRepository;
        this.historyRepository = historyRepository;
        this.policyCacheRepository = policyCacheRepository;
        this.eventRepository = eventRepository;
        this.rabbitTemplate = rabbitTemplate;
    }

    @Transactional
    public Long updatePolicy(Long eventId, PolicyUpdateRequest request) {
        if (!eventRepository.existsById(eventId)) {
            throw new CustomException(EventErrorCode.EVENT_NOT_FOUND);
        }
        Optional<Policy> existingPolicy = policyRepository.findByEventId(eventId);
        Policy policy = existingPolicy.orElse(new Policy(eventId));

        // 기존 정책이 있을 때만 이전 상태를 히스토리에 저장
        if (existingPolicy.isPresent()) {
            historyRepository.save(PolicyHistory.from(policy));
        }

        // TODO(auth): 연동 후 updatedByUserId 전달 (현재 null)
        policy.updatePolicy(
                request.admissionRps(),
                request.admissionConcurrency(),
                request.tokenTtlSeconds(),
                request.blockRules(),
                request.gateMode(),
                null,  // updatedByUserId: 현재 사용자 ID 전달 예정
                request.maxRequestsPerSecond(),
                request.blockDurationMinutes()
        );
        try {
            policyRepository.saveAndFlush(policy);
        } catch (OptimisticLockException | ObjectOptimisticLockingFailureException e) {
            log.warn("Policy update conflict: eventId={}", eventId, e);
            throw new CustomException(PolicyErrorCode.POLICY_CONFLICT);
        }

        try {
            policyCacheRepository.save(eventId, PolicyCacheDto.from(policy));
        } catch (Exception e) {
            log.warn("Redis policy cache save failed: eventId={}", eventId, e);
        }

        // 실시간 반영을 위한 메시지 발행 (Rabbit 활성화 시)
        if (rabbitTemplate != null) {
            rabbitTemplate.convertAndSend("policy.exchange", "policy.updated", policy.getEventId());
        }

        return policy.getPolicyVersion();
    }


/*     정책 조회. Cache-Aside: Redis 확인 → 없으면 DB 조회 후 Redis 저장.
     negative 캐시(exists=false): 정책 없음을 짧은 TTL로 캐시해 캐시 관통 방지.*/
    @Transactional(readOnly = true)
    public PolicyResponse getPolicyByEventId(Long eventId) {
        if (!eventRepository.existsById(eventId)) {
            throw new CustomException(EventErrorCode.EVENT_NOT_FOUND);
        }

        Optional<PolicyCacheDto> cached;
        try {
            cached = policyCacheRepository.find(eventId);
        } catch (Exception e) {
            log.warn("Redis policy cache read failed: eventId={}", eventId, e);
            cached = Optional.empty();
        }
        if (cached.isPresent()) {
            PolicyCacheDto dto = cached.get();
            if (!dto.exists()) {
                throw new CustomException(PolicyErrorCode.POLICY_NOT_FOUND);
            }
            return PolicyResponse.from(dto);
        }

        Optional<Policy> policyOpt = policyRepository.findByEventId(eventId);
        if (policyOpt.isEmpty()) {
            try {
                policyCacheRepository.save(eventId, PolicyCacheDto.negative(eventId));
            } catch (Exception e) {
                log.warn("Redis negative cache save failed: eventId={}", eventId, e);
            }
            throw new CustomException(PolicyErrorCode.POLICY_NOT_FOUND);
        }
        Policy policy = policyOpt.get();
        try {
            policyCacheRepository.save(eventId, PolicyCacheDto.from(policy));
        } catch (Exception e) {
            log.warn("Redis policy cache save failed: eventId={}", eventId, e);
        }
        return PolicyResponse.from(policy);
    }

    @Transactional(readOnly = true)
    public MetricsResponse getMetrics(Long eventId) {
        if (!eventRepository.existsById(eventId)) {
            throw new CustomException(EventErrorCode.EVENT_NOT_FOUND);
        }
        return MetricsResponse.mock();
    }

    // AI 추천 조회
    @Transactional(readOnly = true)
    public AiRecommendationResponse getAiRecommendation(Long eventId) {
        if (!eventRepository.existsById(eventId)) {
            throw new CustomException(EventErrorCode.EVENT_NOT_FOUND);
        }
        return AiRecommendationResponse.mock();
    }

}
