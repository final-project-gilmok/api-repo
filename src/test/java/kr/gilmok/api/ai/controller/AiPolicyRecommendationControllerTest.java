package kr.gilmok.api.ai.controller;

import kr.gilmok.api.ai.dto.AiPolicyRecommendationDto;
import kr.gilmok.api.ai.service.AiPolicyRecommendationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;


import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AiPolicyRecommendationController.class)
@AutoConfigureMockMvc(addFilters = false)
class AiPolicyRecommendationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AiPolicyRecommendationService aiService;

    @Test
    @DisplayName("GET 요청 시 관리자용 AI 트래픽 정책 추천 결과를 반환한다")
    void getLiveAiRecommendation() throws Exception {
        // given
        Long eventId = 1L;
        Long mockAdminUserId = 1L;

        AiPolicyRecommendationDto mockResponse = new AiPolicyRecommendationDto(
                "DECREASE", 100, 50, 300, "컨트롤러 테스트", null
        );

        given(aiService.getRecommendation(eventId, mockAdminUserId)).willReturn(mockResponse);

        // when & then
        mockMvc.perform(get("/admin/events/{eventId}/recommendation", eventId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.actionType").value("DECREASE"))
                .andExpect(jsonPath("$.recommendedAdmissionRps").value(100))
                .andExpect(jsonPath("$.rationale").value("컨트롤러 테스트"));
    }
}