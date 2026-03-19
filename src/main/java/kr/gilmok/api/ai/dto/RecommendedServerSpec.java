package kr.gilmok.api.ai.dto;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

public record RecommendedServerSpec(
        @JsonPropertyDescription("추천 CPU 코어 수")
        Integer cpuCores,

        @JsonPropertyDescription("추천 메모리 (GB)")
        Integer memoryGb,

        @JsonPropertyDescription("추천 레플리카 수")
        Integer replicaCount,

        @JsonPropertyDescription("스펙 변경 필요 여부 (SCALE_UP, SCALE_DOWN, MAINTAIN 중 택 1)")
        String scaleAction,

        @JsonPropertyDescription("스펙 변경 이유 (1~2문장)")
        String reason
) {}
