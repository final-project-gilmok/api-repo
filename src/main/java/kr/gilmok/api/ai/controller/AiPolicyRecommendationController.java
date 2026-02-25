package kr.gilmok.api.ai.controller;

import kr.gilmok.api.ai.dto.AiPolicyRecommendationDto;
import kr.gilmok.api.ai.service.AiPolicyRecommendationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
@org.springframework.context.annotation.Profile("!test")
public class AiPolicyRecommendationController {

    private final AiPolicyRecommendationService aiService;

    // 더미 테스트 엔드포인트를 실제 라이브 데이터 기반 엔드포인트로 변경
    @GetMapping("/events/{eventId}/recommendation")
    public ResponseEntity<AiPolicyRecommendationDto> getLiveAiRecommendation(
            @PathVariable Long eventId
    ) {
        // TODO: 추후 Spring Security가 적용되면 @AuthenticationPrincipal 등을 통해
        // 실제 로그인한 관리자(Admin)의 ID를 가져와야 합니다.
        // 현재는 이슈 2번 DB 저장 테스트를 위해 가짜 관리자 ID(1L)를 하드코딩합니다.
        Long adminUserId = 1L;

        // 서비스 호출: 실제 Redis 큐 데이터와 Prometheus 에러율을 가져와서 AI에게 물어봄
        AiPolicyRecommendationDto response = aiService.getRecommendation(eventId, adminUserId);

        // 결과 반환
        return ResponseEntity.ok(response);
    }
}
