package kr.gilmok.api.policy.repository;

import kr.gilmok.api.policy.entity.PolicyHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PolicyHistoryRepository extends JpaRepository<PolicyHistory, Long> {
    Page<PolicyHistory> findByEventIdOrderByCreatedAtDescIdDesc(Long eventId, Pageable pageable);
    Optional<PolicyHistory> findByIdAndEventId(Long id, Long eventId);
}