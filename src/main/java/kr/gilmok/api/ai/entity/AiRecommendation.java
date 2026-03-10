package kr.gilmok.api.ai.entity;

import lombok.Builder;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "ai_recommendations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AiRecommendation{

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long eventId; // String -> Long 변경

    @Column(nullable = false)
    private Integer inputWindowSec; // 데이터 수집 기준 시간 (예: 60초)

    @Column(columnDefinition = "TEXT", nullable = false)
    private String metricsSnapshotJson; // 수집한 메트릭스 맵을 통째로 JSON 저장

    @Column(columnDefinition = "TEXT", nullable = false)
    private String recommendedPolicyJson; // AI가 응답한 정책 DTO를 통째로 JSON 저장

    @Column(columnDefinition = "TEXT")
    private String rationale; // AI 분석 이유

    @Column(nullable = false)
    private Boolean applied = false; // 정책 적용 여부

    private Integer appliedPolicyVersion; // 적용된 정책 버전 (안 썼으면 null)

    @Column(nullable = false)
    private Long createdByUserId; // 요청한 관리자 ID

    @Column(updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Builder
    public AiRecommendation(Long eventId, Integer inputWindowSec, String metricsSnapshotJson, String recommendedPolicyJson, String rationale, Long createdByUserId) {
        this.eventId = eventId; // ✅ 이 줄이 누락되어 있었다면 꼭 추가하세요!
        this.inputWindowSec = inputWindowSec;
        this.metricsSnapshotJson = metricsSnapshotJson;
        this.recommendedPolicyJson = recommendedPolicyJson;
        this.rationale = rationale;
        this.createdByUserId = createdByUserId;
    }
}