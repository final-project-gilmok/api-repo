package kr.gilmok.api.ai.controller;

import kr.gilmok.api.ai.dto.AiPolicyRecommendationDto;
import kr.gilmok.api.ai.service.AiPolicyRecommendationService;
import kr.gilmok.common.dto.ApiResponse;
import kr.gilmok.common.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin") // 혹은 설계하신 base path
@RequiredArgsConstructor
public class AiPolicyRecommendationController {

    private final AiPolicyRecommendationService aiService;

    @PostMapping("/events/{eventId}/recommendation")
    public ResponseEntity<ApiResponse<AiPolicyRecommendationDto>> getLiveAiRecommendation(
            @PathVariable Long eventId,
            @AuthenticationPrincipal CustomUserDetails userDetails  // ✅ SecurityContext에서 자동 주입
    ) {
        Long adminUserId = userDetails.user().id();  // ✅ record 접근자 사용

        AiPolicyRecommendationDto response = aiService.getRecommendation(eventId, adminUserId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
