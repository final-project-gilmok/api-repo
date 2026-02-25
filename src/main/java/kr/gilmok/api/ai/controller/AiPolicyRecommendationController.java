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
@CrossOrigin(origins = "*") // ⭐️ CORS 에러 방지
public class AiPolicyRecommendationController {

    private final AiPolicyRecommendationService aiService;

    @GetMapping("/events/{eventId}/recommendation")
    public ResponseEntity<ApiResponse<AiPolicyRecommendationDto>> getLiveAiRecommendation(
            @PathVariable Long eventId
    ) {
        Long adminUserId = 1L;
        AiPolicyRecommendationDto response = aiService.getRecommendation(eventId, adminUserId);

        // ⭐️ client.js가 정상 인식하도록 ApiResponse.success 로 감싸기!
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
