package kr.gilmok.api.policy.dto;

import kr.gilmok.api.policy.entity.PolicyHistory;
import kr.gilmok.api.policy.vo.BlockRules;

import java.time.LocalDateTime;

public record PolicyHistoryResponse(
        Long id,
        Long eventId,
        int admissionRps,
        int admissionConcurrency,
        long policyVersion,
        int maxRequestsPerSecond,
        int blockDurationMinutes,
        BlockRules blockRules,
        String gateMode,
        Long updatedByUserId,
        String updatedByUsername,
        LocalDateTime createdAt
){
    public static PolicyHistoryResponse from(PolicyHistory h) {
        return new PolicyHistoryResponse(
                h.getId(),
                h.getEventId(),
                h.getAdmissionRps(),
                h.getAdmissionConcurrency(),
                h.getPolicyVersion(),
                h.getMaxRequestsPerSecond(),
                h.getBlockDurationMinutes(),
                h.getBlockRules(),
                h.getGateMode(),
                h.getUpdatedByUserId(),
                h.getUpdatedByUsername(),
                h.getCreatedAt()
        );
    }
}
