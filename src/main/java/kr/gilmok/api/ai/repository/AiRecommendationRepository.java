package kr.gilmok.api.ai.repository;


import kr.gilmok.api.ai.entity.AiRecommendation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AiRecommendationRepository extends JpaRepository<AiRecommendation, Long> {
    // createdAt 대신 DB가 자동 생성해주는 id 기준으로 내림차순(최신순) 정렬
    List<AiRecommendation> findByEventIdOrderByIdDesc(Long eventId);
}

