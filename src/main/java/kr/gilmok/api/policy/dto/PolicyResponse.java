package kr.gilmok.api.policy.dto;

import kr.gilmok.api.policy.entity.Policy;
import kr.gilmok.api.policy.vo.BlockRules;

public record PolicyResponse(
        Long eventId,
        int admissionRps,
        int admissionConcurrency,
        long policyVersion,
        BlockRules blockRules,
        int maxRequestsPerSecond,
        int blockDurationMinutes,
        String gateMode
) {
    public static PolicyResponse from(Policy policy) {
        return new PolicyResponse(
                policy.getEventId(),
                policy.getAdmissionRps(),
                policy.getAdmissionConcurrency(),
                policy.getPolicyVersion(),
                policy.getBlockRules(),
                policy.getMaxRequestsPerSecond(),
                policy.getBlockDurationMinutes(),
                policy.getGateMode()
        );
    }

    public static PolicyResponse from(PolicyCacheDto dto) {
        return new PolicyResponse(
                dto.eventId(),
                dto.admissionRps(),
                dto.admissionConcurrency(),
                dto.policyVersion(),
                dto.blockRules(),
                dto.maxRequestsPerSecond(),
                dto.blockDurationMinutes(),
                dto.gateMode()
        );
    }
}
