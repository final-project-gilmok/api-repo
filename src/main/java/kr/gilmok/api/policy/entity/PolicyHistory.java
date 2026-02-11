package kr.gilmok.api.policy.entity;

import jakarta.persistence.*;
import kr.gilmok.api.policy.vo.BlockRules;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "policy_histories")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@EntityListeners(AuditingEntityListener.class)
public class PolicyHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long eventId;

    private int admissionRps;
    private int admissionConcurrency;
    private long tokenTtlSeconds;

    @Column(nullable = false)
    private long policyVersion; // 스냅샷 시점의 버전 번호

    @Column(name = "max_requests_per_second", nullable = false)
    private int maxRequestsPerSecond;

    @Column(name = "block_duration_minutes", nullable = false)
    private int blockDurationMinutes;

    @Convert(converter = BlockRulesConverter.class)
    @Column(columnDefinition = "TEXT")
    private BlockRules blockRules;

    @Column(length = 20)
    private String gateMode;

    private Long updatedByUserId;

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt; // 언제 바뀌었는지 기록

    // 스냅샷 객체로 변환
    public static PolicyHistory from(Policy policy) {
        return PolicyHistory.builder()
                .eventId(policy.getEventId())
                .admissionRps(policy.getAdmissionRps())
                .admissionConcurrency(policy.getAdmissionConcurrency())
                .tokenTtlSeconds(policy.getTokenTtlSeconds())
                .blockRules(policy.getBlockRules())
                .policyVersion(policy.getPolicyVersion())
                .maxRequestsPerSecond(policy.getMaxRequestsPerSecond())
                .blockDurationMinutes(policy.getBlockDurationMinutes())
                .gateMode(policy.getGateMode())
                .updatedByUserId(policy.getUpdatedByUserId())
                .build();
    }
}