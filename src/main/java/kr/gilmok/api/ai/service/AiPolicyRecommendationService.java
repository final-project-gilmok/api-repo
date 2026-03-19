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

        // 3. 서버 스펙 섹션 조건부 생성 (총 처리 용량 계산 포함)
        String serverSpecSection = "";
        if (serverSpec != null && serverSpec.hasAnySpec()) {
            int totalCpu = (serverSpec.cpuCores() != null && serverSpec.replicaCount() != null)
                    ? serverSpec.cpuCores() * serverSpec.replicaCount() : 0;
            int totalMemory = (serverSpec.memoryGb() != null && serverSpec.replicaCount() != null)
                    ? serverSpec.memoryGb() * serverSpec.replicaCount() : 0;

            StringBuilder sb = new StringBuilder("[서버 스펙]\n");
            if (serverSpec.instanceType() != null)
                sb.append("- 인스턴스 타입: ").append(serverSpec.instanceType()).append("\n");
            if (serverSpec.cpuCores() != null)
                sb.append("- CPU 코어: ").append(serverSpec.cpuCores()).append("코어 (인스턴스당)\n");
            if (serverSpec.memoryGb() != null)
                sb.append("- 메모리: ").append(serverSpec.memoryGb()).append("GB (인스턴스당)\n");
            if (serverSpec.replicaCount() != null)
                sb.append("- 실행 중인 인스턴스 수: ").append(serverSpec.replicaCount()).append("대\n");
            if (totalCpu > 0)
                sb.append("- 총 처리 가능 CPU: ").append(totalCpu).append("코어\n");
            if (totalMemory > 0)
                sb.append("- 총 처리 가능 메모리: ").append(totalMemory).append("GB\n");

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
                    .system(buildSystemPrompt(serverSpec != null && serverSpec.hasAnySpec()))
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

    /**
     * 서버 스펙 입력 여부에 따라 시스템 프롬프트를 분기합니다.
     */
    private String buildSystemPrompt(boolean hasServerSpec) {
        String specInstruction = hasServerSpec
                ? """
                  [서버 스펙 분석 지침]
                  - 제공된 서버 스펙(인스턴스 타입, CPU, 메모리, 인스턴스 수)을 기반으로 현재 트래픽을 처리할 수 있는지 판단해.
                  - 총 처리 가능 CPU와 메모리를 고려해 admissionRps와 admissionConcurrency를 계산해.
                  - recommendedServerSpec 필드에 반드시 아래 내용을 포함해:
                    * 현재 메트릭 대비 스케일 조정이 필요한지 판단 (SCALE_UP / SCALE_DOWN / MAINTAIN)
                    * 스케일 조정 후 권장 CPU 코어 수, 메모리(GB), 인스턴스 수
                    * 조정 이유를 reason 필드에 한국어로 명확히 작성
                  - 판단 기준:
                    * 큐 대기열이 많고 에러율이 높으면 SCALE_UP
                    * 큐 대기열이 거의 없고 에러율이 낮으면 SCALE_DOWN 고려
                    * 현재 스펙으로 충분히 처리 가능하면 MAINTAIN
                  """
                : """
                  [서버 스펙 미제공]
                  - 서버 스펙 정보가 없으므로 실시간 메트릭만으로 정책을 분석해.
                  - recommendedServerSpec은 null로 반환해.
                  - admissionRps와 admissionConcurrency는 메트릭 기반으로 보수적으로 추천해.
                  """;

        return """
                너는 트래픽 제어 시스템의 수석 SRE 엔지니어 봇이야.
                주어진 서버 메트릭과 스펙을 분석해서 최적의 운영 정책을 JSON으로 반환해.
                
                [역할]
                - 실시간 트래픽 메트릭을 분석해 과부하 여부를 판단한다.
                - 서버가 안정적으로 처리할 수 있는 RPS와 동시 접속 수를 추천한다.
                - 비정상적인 트래픽 패턴(IP, UserAgent)이 감지되면 차단 룰을 제안한다.
                
                """ + specInstruction + """
                
                [응답 형식]
                반드시 아래 JSON 형식만 반환하고, 다른 텍스트는 절대 포함하지 마:
                {
                  "actionType": "SCALE_UP | SCALE_DOWN | MAINTAIN | BLOCK 중 하나",
                  "rationale": "판단 근거를 한국어로 2~3문장으로 작성",
                  "recommendedAdmissionRps": 숫자,
                  "recommendedAdmissionConcurrency": 숫자,
                  "suggestedBlockRules": {
                    "ipRanges": [],
                    "userAgentPatterns": "문자열 또는 null"
                  },
                  "recommendedServerSpec": {
                    "cpuCores": 숫자,
                    "memoryGb": 숫자,
                    "replicaCount": 숫자,
                    "scaleAction": "SCALE_UP | SCALE_DOWN | MAINTAIN 중 하나",
                    "reason": "스케일 조정 이유를 한국어로 작성"
                  }
                }
                """;
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
