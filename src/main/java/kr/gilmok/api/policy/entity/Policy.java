package kr.gilmok.api.policy.entity;

import kr.gilmok.api.policy.vo.BlockRules;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "event_policies")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class) // updated_at 자동 갱신용
public class Policy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true) // 이벤트당 정책은 하나이므로 unique 추가
    private Long eventId;

    private int admissionRps;
    private int admissionConcurrency;
    private long tokenTtlSeconds;

    @Version
    @Column(nullable = false)
    private long policyVersion;

    // 매크로 방어: 유저당 초당 최대 요청 수. 0이면 제한 없음.
    @Column(name = "max_requests_per_second", nullable = false)
    private int maxRequestsPerSecond = 100;

    // 매크로 방어: 초과 시 차단 시간(분).
    @Column(name = "block_duration_minutes", nullable = false)
    private int blockDurationMinutes = 10;

    @Column(length = 20)
    private String gateMode = "ROUTING_ENABLED";

    private Long updatedByUserId;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    @Convert(converter = BlockRulesConverter.class)
    @Column(name = "block_rules_json", columnDefinition = "TEXT") // ERD 컬럼명 일치
    private BlockRules blockRules;

    public Policy(Long eventId) {
        this.eventId = eventId;
        this.policyVersion = 1L;
        this.blockRules = BlockRules.empty();
    }

    public void updatePolicy(int rps, int concurrency, long ttl, BlockRules rules,
                            String gateMode, Long updatedByUserId,
                            Integer maxRequestsPerSecond, Integer blockDurationMinutes) {
        this.admissionRps = rps;
        this.admissionConcurrency = concurrency;
        this.tokenTtlSeconds = ttl;
        this.blockRules = rules != null ? rules : BlockRules.empty();
        if (gateMode != null && !gateMode.isBlank()) {
            this.gateMode = gateMode;
        }
        this.updatedByUserId = updatedByUserId;
        if (maxRequestsPerSecond != null && maxRequestsPerSecond >= 0) {
            this.maxRequestsPerSecond = maxRequestsPerSecond;
        }
        if (blockDurationMinutes != null && blockDurationMinutes >= 0) {
            this.blockDurationMinutes = blockDurationMinutes;
        }
    }
}
