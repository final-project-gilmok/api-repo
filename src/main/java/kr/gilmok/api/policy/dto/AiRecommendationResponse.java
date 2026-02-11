package kr.gilmok.api.policy.dto;

import kr.gilmok.api.policy.vo.BlockRules;

public record AiRecommendationResponse(
        int recommendedRps,
        long recommendedTtlSeconds,
        String rationale,     // 추천 이유
        BlockRules suggestedBlockRules
) {
    public static AiRecommendationResponse mock() {
        return new AiRecommendationResponse(
                200,
                3600,
                "과거 티켓 오픈 5분 전 트래픽 급증 사례를 바탕으로 RPS를 25% 상향 조정할 것을 권장합니다.",
                new BlockRules("10.0.0.0/16", "Python-requests", "ip-base")
        );
    }
}
