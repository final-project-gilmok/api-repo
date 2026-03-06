package kr.gilmok.api.policy.dto;

import kr.gilmok.api.policy.entity.Policy;
import kr.gilmok.api.policy.vo.BlockRules;

/*정책 조회 캐시용 DTO. Redis 저장 후 PolicyResponse로 변환 가능.
exists=false 이면 "정책 없음" negative 캐시*/
public record PolicyCacheDto(
        boolean exists,
        Long eventId,
        int admissionRps,
        int admissionConcurrency,
        long tokenTtlSeconds,
        long policyVersion,
        BlockRules blockRules,
        int maxRequestsPerSecond,
        int blockDurationMinutes,
        String gateMode
) {
    public static PolicyCacheDto from(Policy policy) {
        return new PolicyCacheDto(
                true,
                policy.getEventId(),
                policy.getAdmissionRps(),
                policy.getAdmissionConcurrency(),
                policy.getTokenTtlSeconds(),
                policy.getPolicyVersion(),
                policy.getBlockRules(),
                policy.getMaxRequestsPerSecond(),
                policy.getBlockDurationMinutes(),
                policy.getGateMode()
        );
    }

    // Event는 있으나 Policy가 없는 경우 negative 캐시용
    public static PolicyCacheDto negative(Long eventId) {
        return new PolicyCacheDto(
                false,
                eventId,
                0,
                0,
                0L,
                0L,
                BlockRules.empty(),
                0,
                10,
                null
        );
    }
}
