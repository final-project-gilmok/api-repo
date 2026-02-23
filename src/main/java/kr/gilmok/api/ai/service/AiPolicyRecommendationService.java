package kr.gilmok.api.ai.service;

import kr.gilmok.api.ai.dto.AiPolicyRecommendationDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiPolicyRecommendationService {

    private final ChatClient chatClient;

    public AiPolicyRecommendationDto getRecommendation(String currentMetricsJson) {
        log.info("AI 정책 추천 요청 시작 - 입력 메트릭: {}", currentMetricsJson);

        String systemPrompt = """
                너는 대규모 트래픽을 처리하는 예약 시스템의 수석 SRE(사이트 신뢰성 엔지니어)야.
                주어진 서버 메트릭과 로그 상황을 분석해서, 시스템 다운을 막고 안정적인 서비스를 유지하기 위한
                '대기열 트래픽 제어 정책'을 JSON 형태로만 추천해줘.
                """;

        String userPrompt = "현재 서버 상태 요약: " + currentMetricsJson;

        // Gemini API 호출 및 구조화된 출력(Structured Output) 매핑
        return chatClient.prompt()
                .system(systemPrompt)
                .user(userPrompt)
                .call()
                .entity(AiPolicyRecommendationDto.class);
    }
}