package kr.gilmok.api.policy.dto;

import jakarta.validation.constraints.Min;
import kr.gilmok.api.policy.vo.BlockRules;

/**
 * 이벤트 생성 시 함께 넣을 정책 초기값. 모든 필드 optional, null이면 PolicyDefaults 사용.
 */
public record PolicyCreateRequest(
        @Min(0) Integer admissionRps,
        @Min(0) Integer admissionConcurrency,
        @Min(0) Long tokenTtlSeconds,
        BlockRules blockRules,
        String gateMode,
        @Min(0) Integer maxRequestsPerSecond,
        @Min(0) Integer blockDurationMinutes
) {
}
