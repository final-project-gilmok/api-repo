package kr.gilmok.api.ai.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import kr.gilmok.api.ai.dto.AiPolicyRecommendationDto;
import kr.gilmok.api.ai.entity.AiErrorCode;
import kr.gilmok.api.ai.entity.AiRecommendation;
import kr.gilmok.api.ai.repository.AiRecommendationRepository;
import kr.gilmok.api.queue.service.QueueService;
import kr.gilmok.common.dto.ApiResponse;
import kr.gilmok.common.exception.CustomException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
@org.springframework.context.annotation.Profile("!test")
public class AiPolicyRecommendationService {

    private final ChatClient chatClient;
    private final QueueService queueService;
    private final PrometheusMetricsService prometheusMetricsService;
    private final AiRecommendationRepository aiRecommendationRepository;
    private final ObjectMapper objectMapper;

    // eventId와 요청한 관리자 ID를 함께 받습니다.
    public AiPolicyRecommendationDto getRecommendation(Long eventId, Long adminUserId) {

        // 1. 실제 데이터 수집
        // eventId를 Long으로 넘겨주거나 내부적으로 String 변환 처리 필요 (queueService 스펙에 맞춤)
        Map<String, Long> queueMetrics = queueService.getQueueMetricsForAi(String.valueOf(eventId));
        String errorRateInfo = prometheusMetricsService.getCurrentErrorRate();

        // 2. 스냅샷 데이터(Map) 구성
        Map<String, Object> snapshotData = new HashMap<>(queueMetrics);
        snapshotData.put("errorRate", errorRateInfo);

        long waitingSize = queueMetrics.getOrDefault("waitingQueueSize", 0L);
        long currentRps = queueMetrics.getOrDefault("currentRps", 0L);

        // 3. 프롬프트 동적 생성
        String promptMessage = String.format(
                "현재 서버 상태를 분석해서 트래픽 제어 정책을 JSON으로 추천해줘.\n" +
                        "- 현재 큐 대기열: %d명\n" +
                        "- 현재 유입 RPS: %d\n" +
                        "- 최근 1분간 5xx 에러율: %s",
                waitingSize, currentRps, errorRateInfo
        );

        log.info("Sending prompt to AI: {}", promptMessage);

        // 4. AI 호출
        AiPolicyRecommendationDto responseDto;
        try {
            responseDto = chatClient.prompt()
                    .system("너는 트래픽 제어 시스템의 수석 SRE 엔지니어 봇이야.")
                    .user(promptMessage)
                    .call()
                    .entity(AiPolicyRecommendationDto.class);
        } catch (Exception e) {
            log.error("AI 호출 실패: eventId={}", eventId, e);
            throw new CustomException(AiErrorCode.AI_RECOMMENDATION_FAILED); // AI001
        }

        // 5. DB 저장
        // ✅ 직렬화 실패 → AI002 (예외 전파), DB 저장 실패 → AI005 (로그만, 예외 미전파)
        saveRecommendationToDb(eventId, adminUserId, snapshotData, responseDto);

        return responseDto;
    }

    private void saveRecommendationToDb(Long eventId, Long adminUserId,
                                        Map<String, Object> snapshotData,
                                        AiPolicyRecommendationDto dto) {

        // 1. 직렬화 (실패 시 AI002 예외)
        String metricsJson;
        String policyJson;
        try {
            metricsJson = objectMapper.writeValueAsString(snapshotData);
            policyJson = objectMapper.writeValueAsString(dto);
        } catch (JsonProcessingException e) {
            log.error("AI 추천 직렬화 실패: eventId={}", eventId, e);
            throw new CustomException(AiErrorCode.AI_SERIALIZATION_FAILED); // AI002
        }

        // 2. DB 저장 (실패해도 AI 응답은 이미 반환 예정 → 로그만 남김)
        try {
            AiRecommendation entity = AiRecommendation.builder()
                    .eventId(eventId)
                    .inputWindowSec(60)
                    .metricsSnapshotJson(metricsJson)
                    .recommendedPolicyJson(policyJson)
                    .rationale(dto.rationale())
                    .createdByUserId(adminUserId)
                    .build();

            aiRecommendationRepository.save(entity);
            log.info("AI recommendation saved to DB: eventId={}, adminId={}", eventId, adminUserId);

        } catch (DataAccessException e) {
            // 규칙 준수: 에러코드로 의미를 명확히 하되, 전파는 하지 않음
            CustomException customEx = new CustomException(AiErrorCode.AI_DB_SAVE_FAILED); // AI003
            log.error("AI 추천 DB 저장 실패 [{}]: eventId={}",
                    customEx.getErrorCode().getCode(), eventId, e);
        }
    }
}
