package kr.gilmok.api.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.gilmok.api.ai.dto.AiPolicyRecommendationDto;
import kr.gilmok.api.ai.entity.AiRecommendation;
import kr.gilmok.api.ai.repository.AiRecommendationRepository;
import kr.gilmok.api.queue.service.QueueService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiPolicyRecommendationServiceTest {

    // InjectMocks를 쓰지 않고, BeforeEach에서 수동으로 조립합니다.
    private AiPolicyRecommendationService aiService;

    @Mock
    private QueueService queueService;

    @Mock
    private PrometheusMetricsService prometheusMetricsService;

    @Mock
    private AiRecommendationRepository aiRecommendationRepository;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    // 복잡한 Spec들을 모킹하는 대신 ChatClient를 직접 모킹합니다.
    @Mock
    private ChatClient chatClient;

    // 체이닝 문법을 모두 통과시키기 위한 가장 깊은 반환 객체(최종 반환자)
    @Mock
    private ChatClient.CallResponseSpec callResponseSpec;

    // 체이닝을 받아주는 껍데기 객체 (모든 체인 호출이 결국 자신을 리턴하도록 세팅)
    @Mock
    private ChatClient.ChatClientRequestSpec requestSpec;

    @BeforeEach
    void setUp() {
        // [핵심 해결책] ChatClient의 복잡한 체이닝(.system().user().call().entity())을
        // 한 줄 한 줄 따라가는 대신, Mockito의 깊은 스텁(Deep Stubs) 기능과 유사하게 직접 연결해 줍니다.

        // 1. chatClient.prompt() 호출 시 requestSpec 껍데기 리턴
        lenient().when(chatClient.prompt()).thenReturn(requestSpec);

        // 2. requestSpec의 system(), user() 호출 시 자기 자신(requestSpec)을 다시 리턴
        lenient().when(requestSpec.system(anyString())).thenReturn(requestSpec);
        lenient().when(requestSpec.user(anyString())).thenReturn(requestSpec);

        // 3. requestSpec.call() 호출 시 최종 응답을 뱉어낼 callResponseSpec 리턴
        lenient().when(requestSpec.call()).thenReturn(callResponseSpec);

        // 서비스 객체 수동 생성 (Mock 주입)
        aiService = new AiPolicyRecommendationService(
                chatClient,
                queueService,
                prometheusMetricsService,
                aiRecommendationRepository,
                objectMapper
        );
    }

    @Test
    @DisplayName("실제 메트릭을 수집하여 AI 정책을 추천받고 DB에 JSON 형태로 저장한다")
    void getRecommendation_Success() {
        // given
        Long eventId = 1L;
        Long adminUserId = 100L;

        // 큐 데이터 모킹
        Map<String, Long> mockQueueMetrics = new HashMap<>();
        mockQueueMetrics.put("waitingQueueSize", 5000L);
        given(queueService.getQueueMetricsForAi(String.valueOf(eventId))).willReturn(mockQueueMetrics);

        // 프로메테우스 데이터 모킹
        given(prometheusMetricsService.getCurrentErrorRate()).willReturn("4.50%");

        // AI 최종 반환 DTO 모킹
        AiPolicyRecommendationDto mockAiResponse = new AiPolicyRecommendationDto(
                "DECREASE", 50, 20, "부하 심각", null
        );

        // 4. callResponseSpec.entity(Dto.class) 호출 시 우리가 만든 가짜 DTO 리턴
        given(callResponseSpec.entity(AiPolicyRecommendationDto.class)).willReturn(mockAiResponse);

        ArgumentCaptor<AiRecommendation> entityCaptor = ArgumentCaptor.forClass(AiRecommendation.class);

        // when
        AiPolicyRecommendationDto result = aiService.getRecommendation(eventId, adminUserId);

        // then
        assertThat(result.actionType()).isEqualTo("DECREASE");

        verify(aiRecommendationRepository, times(1)).save(entityCaptor.capture());
        AiRecommendation savedEntity = entityCaptor.getValue();

        assertThat(savedEntity.getEventId()).isEqualTo(eventId);
        assertThat(savedEntity.getCreatedByUserId()).isEqualTo(adminUserId);
        assertThat(savedEntity.getMetricsSnapshotJson()).contains("5000").contains("4.50%");
        assertThat(savedEntity.getRecommendedPolicyJson()).contains("DECREASE");
    }
}