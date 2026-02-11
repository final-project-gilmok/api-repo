package kr.gilmok.api.event.repository;

import kr.gilmok.api.event.entity.Event;
import kr.gilmok.api.event.entity.EventStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EventRepository extends JpaRepository<Event, Long> {

    List<Event> findByStatusOrderByStartsAtDesc(EventStatus status);
}
