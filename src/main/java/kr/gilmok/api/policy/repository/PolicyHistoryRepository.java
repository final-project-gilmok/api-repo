package kr.gilmok.api.policy.repository;

import kr.gilmok.api.policy.entity.PolicyHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PolicyHistoryRepository extends JpaRepository<PolicyHistory, Long> {

    // 특정 이벤트의 과거 정책 변경 이력 최신순 조회
    List<PolicyHistory> findAllByEventIdOrderByCreatedAtDesc(Long eventId);

    List<PolicyHistory> findAllByEventIdAndPolicyVersion(Long eventId, Long policyVersion);
}