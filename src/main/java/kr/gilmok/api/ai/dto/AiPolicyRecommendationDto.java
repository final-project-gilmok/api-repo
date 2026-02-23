package kr.gilmok.api.ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import kr.gilmok.api.policy.vo.BlockRules;

public record AiPolicyRecommendationDto(
        @JsonPropertyDescription("트래픽 제어 정책 권장 방향 (INCREASE, DECREASE, MAINTAIN 중 택 1)")
        @JsonProperty(required = true)
        String actionType,

        @JsonPropertyDescription("추천 초당 입장 허용 수 (RPS, 10~500 사이)")
        @JsonProperty(required = true)
        int recommendedAdmissionRps,

        @JsonPropertyDescription("추천 입장 허용 동시성(Concurrency) 수치")
        @JsonProperty(required = true)
        int recommendedAdmissionConcurrency,

        @JsonPropertyDescription("추천 입장 토큰 유효 시간(초)")
        @JsonProperty(required = true)
        long recommendedTokenTtlSeconds,

        @JsonPropertyDescription("추천 이유 (최대 3문장 이내)")
        @JsonProperty(required = true)
        String rationale,

        @JsonPropertyDescription("추천하는 차단 규칙")
        @JsonProperty(required = true)
        BlockRules suggestedBlockRules
) {}
