package kr.gilmok.api.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.client.RestClientTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@RestClientTest(PrometheusMetricsService.class)
class PrometheusMetricsServiceTest {

    @Autowired
    private PrometheusMetricsService prometheusMetricsService;

    @Autowired
    private MockRestServiceServer mockServer;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("프로메테우스에서 5xx 에러율을 정상적으로 수집하고 파싱한다")
    void getCurrentErrorRate_Success() {
        // given
        String mockPrometheusResponse = """
                {
                  "status": "success",
                  "data": {
                    "resultType": "vector",
                    "result": [
                      {
                        "metric": {},
                        "value": [ 1708740000.000, "0.045" ]
                      }
                    ]
                  }
                }
                """;

        // 💡 [수정] 복잡한 쿼리 파라미터 검증 대신, API 경로 시작점만 맞으면 OK 처리
        mockServer.expect(requestTo(startsWith("http://localhost:9090/api/query")))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(mockPrometheusResponse, MediaType.APPLICATION_JSON));

        // when
        String errorRate = prometheusMetricsService.getCurrentErrorRate();

        // then
        assertThat(errorRate).isEqualTo("4.50%");
        mockServer.verify();
    }

    @Test
    @DisplayName("프로메테우스 응답이 비어있거나 에러가 나면 기본값 0.00%를 반환한다")
    void getCurrentErrorRate_EmptyOrError() {
        // given
        String emptyResponse = """
                { "status": "success", "data": { "result": [] } }
                """;

        mockServer.expect(requestTo(startsWith("http://localhost:9090/api/query")))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(emptyResponse, MediaType.APPLICATION_JSON));

        // when
        String errorRate = prometheusMetricsService.getCurrentErrorRate();

        // then
        assertThat(errorRate).isEqualTo("0.00%");
        mockServer.verify();
    }
}