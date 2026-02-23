package kr.gilmok.api.ai.controller;

import kr.gilmok.api.ai.dto.AiPolicyRecommendationDto;
import kr.gilmok.api.ai.service.AiPolicyRecommendationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/ai")
@RequiredArgsConstructor
public class AiPolicyRecommendationController {

    private final AiPolicyRecommendationService aiService;

    @GetMapping("/test")
    public ResponseEntity<AiPolicyRecommendationDto> testAiRecommendation() {
        // DB나 Prometheus를 연결하기 전, 이슈 1번 테스트를 위한 가짜(Dummy) 메트릭 상황 가정
        String dummyMetrics = """
                {
                  "queue_waiting_size": 15000,
                  "admission_rps": 200,
                  "cpu_usage_percent": 92,
                  "error_rate_5xx_percent": 4.5,
                  "recent_abnormal_user_agent": "Python-requests/2.25.1"
                }
                """;

        // 서비스 호출 (Gemini 연동)
        AiPolicyRecommendationDto response = aiService.getRecommendation(dummyMetrics);

        // 결과 반환
        return ResponseEntity.ok(response);
    }
}