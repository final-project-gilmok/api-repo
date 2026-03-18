package kr.gilmok.api.ai.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import kr.gilmok.api.ai.dto.AiPolicyRecommendationDto;
import kr.gilmok.api.ai.dto.ServerSpecRequest;
import kr.gilmok.api.ai.entity.AiErrorCode;
import kr.gilmok.api.ai.entity.AiRecommendation;
import kr.gilmok.api.ai.repository.AiRecommendationRepository;
import kr.gilmok.api.queue.service.QueueService;
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

    public AiPolicyRecommendationDto getRecommendation(Long eventId, Long adminUserId,
                                                       ServerSpecRequest serverSpec) {
        // 1. 데이터 수집
        Map<String, Long> queueMetrics = queueService.getQueueMetricsForAi(String.valueOf(eventId));
        String errorRateInfo = prometheusMetricsService.getCurrentErrorRate();

        // 2. 스냅샷 구성
        Map<String, Object> snapshotData = new HashMap<>(queueMetrics);
        snapshotData.put("errorRate", errorRateInfo);
        if (serverSpec != null && serverSpec.hasAnySpec()) {
            snapshotData.put("serverSpec", serverSpec);
        }

        long waitingSize = queueMetrics.getOrDefault("waitingQueueSize", 0L);
        long currentRps = queueMetrics.getOrDefault("currentRps", 0L);

        // 3. 서버 스펙 섹션 조건부 생성
        String serverSpecSection = "";
        if (serverSpec != null && serverSpec.hasAnySpec()) {
            StringBuilder sb = new StringBuilder("[서버 스펙]\n");
            if (serverSpec.instanceType() != null)
                sb.append("- 인스턴스 타입: ").append(serverSpec.instanceType()).append("\n");
            if (serverSpec.cpuCores() != null)
                sb.append("- CPU 코어: ").append(serverSpec.cpuCores()).append("개\n");
            if (serverSpec.memoryGb() != null)
                sb.append("- 메모리: ").append(serverSpec.memoryGb()).append("GB\n");
            if (serverSpec.replicaCount() != null)
                sb.append("- 레플리카 수: ").append(serverSpec.replicaCount()).append("대\n");
            serverSpecSection = sb.toString() + "\n";
        }

        // 4. 프롬프트 구성
        String promptMessage = String.format(
                "%s" +
                        "[실시간 메트릭]\n" +
                        "- 현재 큐 대기열: %d명\n" +
                        "- 현재 유입 RPS: %d\n" +
                        "- 최근 1분간 5xx 에러율: %s",
                serverSpecSection, waitingSize, currentRps, errorRateInfo
        );

        log.info("Sending prompt to AI: {}", promptMessage);

        // 5. AI 호출
        AiPolicyRecommendationDto responseDto;
        try {
            responseDto = chatClient.prompt()
                    .system("""
                            너는 트래픽 제어 시스템의 수석 SRE 엔지니어 봇이야.
                            서버 스펙 정보가 주어진 경우 recommendedServerSpec 필드에 스펙 조정 추천을 반드시 포함해줘.
                            서버 스펙 정보가 없으면 recommendedServerSpec은 null로 반환해줘.
                            """)
                    .user(promptMessage)
                    .call()
                    .entity(AiPolicyRecommendationDto.class);
        } catch (Exception e) {
            log.error("AI 호출 실패: eventId={}", eventId, e);
            throw new CustomException(AiErrorCode.AI_RECOMMENDATION_FAILED);
        }

        // 6. DB 저장
        saveRecommendationToDb(eventId, adminUserId, snapshotData, responseDto);

        return responseDto;
    }

    private void saveRecommendationToDb(Long eventId, Long adminUserId,
                                        Map<String, Object> snapshotData,
                                        AiPolicyRecommendationDto dto) {
        String metricsJson;
        String policyJson;
        try {
            metricsJson = objectMapper.writeValueAsString(snapshotData);
            policyJson = objectMapper.writeValueAsString(dto);
        } catch (JsonProcessingException e) {
            log.error("AI 추천 직렬화 실패: eventId={}", eventId, e);
            throw new CustomException(AiErrorCode.AI_SERIALIZATION_FAILED);
        }

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
            CustomException customEx = new CustomException(AiErrorCode.AI_DB_SAVE_FAILED);
            log.error("AI 추천 DB 저장 실패 [{}]: eventId={}",
                    customEx.getErrorCode().getCode(), eventId, e);
        }
    }
}
