package kr.gilmok.api.policy.service;

import kr.gilmok.api.event.exception.EventErrorCode;
import kr.gilmok.api.event.repository.EventRepository;
import kr.gilmok.api.policy.constants.PolicyDefaults;
import kr.gilmok.api.policy.dto.PolicyCacheDto;
import kr.gilmok.api.policy.dto.PolicyCreateRequest;
import kr.gilmok.api.policy.dto.PolicyResponse;
import kr.gilmok.api.policy.dto.PolicyHistoryResponse;
import kr.gilmok.api.policy.dto.PolicyUpdateRequest;
import kr.gilmok.api.policy.entity.Policy;
import kr.gilmok.api.policy.entity.PolicyHistory;
import kr.gilmok.api.policy.exception.PolicyErrorCode;
import kr.gilmok.api.policy.repository.PolicyCacheRepository;
import kr.gilmok.api.policy.repository.PolicyHistoryRepository;
import kr.gilmok.api.policy.repository.PolicyRepository;
import kr.gilmok.api.policy.validation.BlockRulesValidator;
import kr.gilmok.common.exception.CustomException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.extern.slf4j.Slf4j;

import java.util.Objects;
import java.util.Optional;

@Slf4j
@Service
@Transactional(readOnly = true)
public class PolicyService {

    private final PolicyRepository policyRepository;
    private final PolicyHistoryRepository historyRepository;
    private final PolicyCacheRepository policyCacheRepository;
    private final EventRepository eventRepository;

    public PolicyService(PolicyRepository policyRepository,
                         PolicyHistoryRepository historyRepository,
                         PolicyCacheRepository policyCacheRepository,
                         EventRepository eventRepository) {
        this.policyRepository = policyRepository;
        this.historyRepository = historyRepository;
        this.policyCacheRepository = policyCacheRepository;
        this.eventRepository = eventRepository;
    }

    public Page<PolicyHistoryResponse> getPolicyHistories(Long eventId, Pageable pageable) {
        if (!eventRepository.existsById(eventId)) {
            throw new CustomException(EventErrorCode.EVENT_NOT_FOUND);
        }
        return historyRepository.findByEventIdOrderByCreatedAtDesc(eventId, pageable)
                .map(PolicyHistoryResponse::from);
    }

    @Transactional
    public PolicyResponse rollbackPolicy(Long eventId, Long historyId, Long rollbackByUserId, String rollbackByUsername) {
        Objects.requireNonNull(rollbackByUserId, "rollbackByUserId must not be null");
        if (!eventRepository.existsById(eventId)) {
            throw new CustomException(EventErrorCode.EVENT_NOT_FOUND);
        }
        PolicyHistory history = historyRepository.findByIdAndEventId(historyId, eventId)
                .orElseThrow(() -> new CustomException(PolicyErrorCode.POLICY_HISTORY_NOT_FOUND));
        Policy policy = policyRepository.findByEventId(eventId)
                .orElseThrow(() -> new CustomException(PolicyErrorCode.POLICY_NOT_FOUND));
        // 롤백 전 현재 상태를 히스토리에 저장
        historyRepository.save(PolicyHistory.from(policy));
        // 히스토리 적용
        policy.applyFromHistory(history, rollbackByUserId, rollbackByUsername);
        try {
            policyRepository.saveAndFlush(policy);
        } catch (ObjectOptimisticLockingFailureException e) {
            log.warn("Policy rollback conflict: eventId={}", eventId, e);
            throw new CustomException(PolicyErrorCode.POLICY_CONFLICT);
        }
        // 캐시 갱신
        try {
            policyCacheRepository.save(eventId, PolicyCacheDto.from(policy));
        } catch (Exception e) {
            log.warn("Redis policy cache save failed after rollback: eventId={}", eventId, e);
        }
        return PolicyResponse.from(policy);
    }

    @Transactional
    public void createPolicyForEvent(Long eventId, PolicyCreateRequest request) {
        if (request != null && request.blockRules() != null) {
            BlockRulesValidator.validate(request.blockRules());
        }
        Policy policy = new Policy(eventId);
        int rps = effectiveAdmissionRps(request);
        int concurrency = effectiveAdmissionConcurrency(request);
        var blockRules = request != null && request.blockRules() != null ? request.blockRules() : PolicyDefaults.blockRules();
        String gateMode = effectiveGateMode(request);
        Integer maxRps = request != null ? request.maxRequestsPerSecond() : null;
        Integer blockMin = request != null ? request.blockDurationMinutes() : null;
        policy.updatePolicy(rps, concurrency, blockRules, gateMode, null, null, maxRps, blockMin);
        policyRepository.saveAndFlush(policy);
        try {
            policyCacheRepository.save(eventId, PolicyCacheDto.from(policy));
        } catch (Exception e) {
            log.warn("Redis policy cache save failed: eventId={}", eventId, e);
        }
    }

    @Transactional
    public PolicyResponse updatePolicy(Long eventId, PolicyUpdateRequest request, Long updatedByUserId, String updatedByUsername) {
        Objects.requireNonNull(updatedByUserId, "updatedByUserId must not be null");
        Objects.requireNonNull(updatedByUsername, "updatedByUsername must not be null");
        if (!eventRepository.existsById(eventId)) {
            throw new CustomException(EventErrorCode.EVENT_NOT_FOUND);
        }
        BlockRulesValidator.validate(request.blockRules());

        Optional<Policy> existingPolicy = policyRepository.findByEventId(eventId);
        Policy policy = existingPolicy.orElse(new Policy(eventId));

        // 기존 정책이 있을 때만 이전 상태를 히스토리에 저장
        if (existingPolicy.isPresent()) {
            historyRepository.save(PolicyHistory.from(policy));
        }

        policy.updatePolicy(
                request.admissionRps(),
                request.admissionConcurrency(),
                request.blockRules(),
                request.gateMode(),
                updatedByUserId,
                updatedByUsername,
                request.maxRequestsPerSecond(),
                request.blockDurationMinutes()
        );
        try {
            policyRepository.saveAndFlush(policy);
        } catch (ObjectOptimisticLockingFailureException e) {
            log.warn("Policy update conflict: eventId={}", eventId, e);
            throw new CustomException(PolicyErrorCode.POLICY_CONFLICT);
        }

        try {
            policyCacheRepository.save(eventId, PolicyCacheDto.from(policy));
        } catch (Exception e) {
            log.warn("Redis policy cache save failed: eventId={}", eventId, e);
        }


        return PolicyResponse.from(policy);
    }


/*     정책 조회. Cache-Aside: Redis 우선 조회 → 미스 시에만 DB, 이벤트 검증.
     negative 캐시(exists=false): 정책 없음을 짧은 TTL로 캐시해 캐시 관통 방지.*/
    public PolicyResponse getPolicyByEventId(Long eventId) {
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
            if (!eventRepository.existsById(eventId)) {
                throw new CustomException(EventErrorCode.EVENT_NOT_FOUND);
            }
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

    /** 이벤트 close 시 해당 이벤트의 정책 캐시를 무효화한다. */
    public void evictPolicyCache(Long eventId) {
        try {
            policyCacheRepository.evict(eventId);
        } catch (Exception e) {
            log.warn("Policy cache evict failed: eventId={}", eventId, e);
        }
    }

    private static int effectiveAdmissionRps(PolicyCreateRequest request) {
        return request != null && request.admissionRps() != null ? request.admissionRps() : PolicyDefaults.ADMISSION_RPS;
    }

    private static int effectiveAdmissionConcurrency(PolicyCreateRequest request) {
        return request != null && request.admissionConcurrency() != null ? request.admissionConcurrency() : PolicyDefaults.ADMISSION_CONCURRENCY;
    }

    private static String effectiveGateMode(PolicyCreateRequest request) {
        return request != null && request.gateMode() != null && !request.gateMode().isBlank() ? request.gateMode() : PolicyDefaults.GATE_MODE;
    }
}
