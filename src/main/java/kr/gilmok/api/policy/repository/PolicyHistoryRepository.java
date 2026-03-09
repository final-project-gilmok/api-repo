package kr.gilmok.api.policy.repository;

import kr.gilmok.api.policy.entity.PolicyHistory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PolicyHistoryRepository extends JpaRepository<PolicyHistory, Long> {
}