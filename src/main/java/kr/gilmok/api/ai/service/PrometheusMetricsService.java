package kr.gilmok.api.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Slf4j
@Service
@org.springframework.context.annotation.Profile("!test")
public class PrometheusMetricsService {
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public PrometheusMetricsService(@Value("${metrics.prometheus.url}") String prometheusUrl,
                                    RestClient.Builder restClientBuilder,
                                    ObjectMapper objectMapper) {

        // 1. HTTP 타임아웃 팩토리 생성 (연결 3초, 읽기 5초)
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(5));

        // 2. RestClient 빌더에 팩토리 주입
        this.restClient = restClientBuilder
                .baseUrl(prometheusUrl)
                .requestFactory(factory) // 💡 타임아웃 적용
                .build();

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

            if (resultNode.isArray() && !resultNode.isEmpty()) {
                // 💡 [수정] get() 대신 모두 path()를 사용하여 NPE 원천 차단
                // 값이 없으면 MissingNode가 반환되고, asText()는 빈 문자열("")을 반환함
                String valueStr = resultNode.path(0).path("value").path(1).asText();

                // 빈 문자열이 아닐 때만 파싱하여 NumberFormatException 방지
                if (!valueStr.isBlank()) {
                    double errorRate = Double.parseDouble(valueStr) * 100;
                    if (Double.isFinite(errorRate)) {
                        return String.format("%.2f%%", errorRate);
                    }
                }
            }
            return "0.00%"; // 에러가 없거나 데이터가 없을 때

        } catch (Exception e) {
            log.warn("Failed to fetch error rate from Prometheus", e);
            return "데이터 수집 실패";
        }
    }
}
