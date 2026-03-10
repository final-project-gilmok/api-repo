package kr.gilmok.api.policy.dto;

import jakarta.validation.constraints.Min;
import kr.gilmok.api.policy.vo.BlockRules;

public record PolicyUpdateRequest(
        @Min(0) int admissionRps,
        @Min(0) int admissionConcurrency,
        BlockRules blockRules,
        // 게이트 모드(null이면 기존값 유지)
        String gateMode,
        // 매크로 방어: 유저당 초당 최대 요청 수. 0이면 제한 없음. null이면 기존값 유지.
        @Min(0) Integer maxRequestsPerSecond,
        // 매크로 방어: 초과 시 차단 시간(분). null이면 기존값 유지.
        @Min(0) Integer blockDurationMinutes
) {
    public PolicyUpdateRequest(int admissionRps, int admissionConcurrency, BlockRules blockRules) {
        this(admissionRps, admissionConcurrency, blockRules, null, null, null);
    }
}
