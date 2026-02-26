package kr.gilmok.api.ai.controller;

import kr.gilmok.api.ai.dto.AiPolicyRecommendationDto;
import kr.gilmok.api.ai.service.AiPolicyRecommendationService;
import kr.gilmok.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin") // 혹은 설계하신 base path
@RequiredArgsConstructor
 @CrossOrigin(origins = "*") //-> 🚨 운영에서는 지우고 WebMvcConfigurer(글로벌 CORS)에서 도메인을 제한하는 것이 좋습니다.
public class AiPolicyRecommendationController {

    private final AiPolicyRecommendationService aiService;

    @PostMapping("/events/{eventId}/recommendation")
    public ResponseEntity<ApiResponse<AiPolicyRecommendationDto>> getLiveAiRecommendation(
            @PathVariable Long eventId
    ) {
        // TODO(auth): 추후 Spring Security 적용 시 @AuthenticationPrincipal을 통해 실제 관리자 ID 주입 필요
        Long adminUserId = 1L;

        AiPolicyRecommendationDto response = aiService.getRecommendation(eventId, adminUserId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
