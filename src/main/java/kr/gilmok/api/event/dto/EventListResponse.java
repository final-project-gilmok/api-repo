package kr.gilmok.api.event.dto;

import kr.gilmok.api.event.entity.Event;
import kr.gilmok.api.event.entity.EventStatus;

import java.time.LocalDateTime;

public record EventListResponse(
        Long eventId,
        String name,
        String description,
        EventStatus status,
        LocalDateTime startsAt,
        LocalDateTime endsAt
) {
    public static EventListResponse from(Event event) {
        return new EventListResponse(
                event.getId(),
                event.getName(),
                event.getDescription(),
                event.getStatus(),
                event.getStartsAt(),
                event.getEndsAt()
        );
    }
}
