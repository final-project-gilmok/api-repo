package kr.gilmok.api.policy.dto;

public record MetricsResponse(
        int currentRps,      // 현재 초당 요청 수
        long p95LatencyMs,   // 상위 95% 응답 속도
        int queueLength,     // 대기열 길이
        int activeUsers      // 현재 접속자 수
) {
    // 테스트용 Mock 데이터 생성 메서드
    public static MetricsResponse mock() {
        return new MetricsResponse(150, 45, 1200, 5000);
    }
}
