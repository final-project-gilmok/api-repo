package kr.gilmok.api.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Slf4j
@Service
public class PrometheusMetricsService {
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public PrometheusMetricsService(@Value("${metrics.prometheus.url}") String prometheusUrl,
                                    RestClient.Builder restClientBuilder,
                                    ObjectMapper objectMapper) {
        this.restClient = restClientBuilder.baseUrl(prometheusUrl).build();
        this.objectMapper = objectMapper;
    }

    public String getCurrentErrorRate() {
        String promQl = "sum(rate(http_server_requests_seconds_count{status=~\"5..\"}[1m])) / sum(rate(http_server_requests_seconds_count[1m]))";

        try {
            // 💡 [수정] uriBuilder 내부의 중괄호 파싱 충돌을 막기 위해
            // uri(String uri, Map<String, ?> uriVariables) 형태를 사용합니다.
            String response = restClient.get()
                    .uri("/api/query?query={query}", promQl)
                    .retrieve()
                    .body(String.class);

            // JsonNode를 사용해 data -> result -> [0] -> value -> [1] (실제 수치) 만 추출
            JsonNode root = objectMapper.readTree(response);
            JsonNode resultNode = root.path("data").path("result");

            if (resultNode.isArray() && resultNode.size() > 0) {
                // 프로메테우스 value 배열의 두 번째 요소가 실제 문자열 수치입니다. (예: "0.045")
                String valueStr = resultNode.get(0).path("value").get(1).asText();
                double errorRate = Double.parseDouble(valueStr) * 100; // %로 변환
                return String.format("%.2f%%", errorRate); // "4.50%" 형태로 반환
            }
            return "0.00%"; // 에러가 없거나 데이터가 없을 때

        } catch (Exception e) {
            log.warn("Failed to fetch error rate from Prometheus", e);
            return "데이터 수집 실패";
        }
    }
}
