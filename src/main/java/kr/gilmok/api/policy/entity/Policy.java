package kr.gilmok.api.policy.entity;

import kr.gilmok.api.policy.constants.PolicyDefaults;
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

    private int admissionRps; // 초당 허용 수
    private int admissionConcurrency; // 동시 접속 수

    @Version
    @Column(nullable = false)
    private long policyVersion; // 동시성 제어 -> 낙관적 락

    // 매크로 방어: 유저당 초당 최대 요청 수. 0이면 제한 없음.
    @Column(name = "max_requests_per_second", nullable = false)
    private int maxRequestsPerSecond = PolicyDefaults.MAX_REQUESTS_PER_SECOND;

    // 매크로 방어: 초과 시 차단 시간(분).
    @Column(name = "block_duration_minutes", nullable = false)
    private int blockDurationMinutes = PolicyDefaults.BLOCK_DURATION_MINUTES;

    @Column(length = 20)
    private String gateMode = PolicyDefaults.GATE_MODE;

    private Long updatedByUserId;

    @Column(length = 50)
    private String updatedByUsername;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    @Convert(converter = BlockRulesConverter.class)
    @Column(name = "block_rules_json", columnDefinition = "TEXT") // ERD 컬럼명 일치
    private BlockRules blockRules;

    public Policy(Long eventId) {
        this.eventId = eventId;
        this.policyVersion = 1L;
        this.admissionRps = PolicyDefaults.ADMISSION_RPS;
        this.admissionConcurrency = PolicyDefaults.ADMISSION_CONCURRENCY;
        this.blockRules = PolicyDefaults.blockRules();
    }

    public void updatePolicy(int rps, int concurrency, BlockRules rules,
                            String gateMode, Long updatedByUserId, String updatedByUsername,
                            Integer maxRequestsPerSecond, Integer blockDurationMinutes) {
        this.admissionRps = rps;
        this.admissionConcurrency = concurrency;
        this.blockRules = rules != null ? rules : BlockRules.empty();
        if (gateMode != null && !gateMode.isBlank()) {
            this.gateMode = gateMode;
        }
        this.updatedByUserId = updatedByUserId;
        this.updatedByUsername = updatedByUsername;
        if (maxRequestsPerSecond != null && maxRequestsPerSecond >= 0) {
            this.maxRequestsPerSecond = maxRequestsPerSecond;
        }
        if (blockDurationMinutes != null && blockDurationMinutes >= 0) {
            this.blockDurationMinutes = blockDurationMinutes;
        }
    }

    public void applyFromHistory(PolicyHistory history, Long rollbackByUserId, String rollbackByUsername) {
        this.admissionRps = history.getAdmissionRps();
        this.admissionConcurrency = history.getAdmissionConcurrency();
        this.blockRules = history.getBlockRules();
        if (history.getGateMode() != null && !history.getGateMode().isBlank()) {
            this.gateMode = history.getGateMode();
        }
        this.maxRequestsPerSecond = history.getMaxRequestsPerSecond();
        this.blockDurationMinutes = history.getBlockDurationMinutes();
        this.updatedByUserId = rollbackByUserId;
        this.updatedByUsername = rollbackByUsername;
    }
}
