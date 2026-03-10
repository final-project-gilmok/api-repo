package kr.gilmok.api.event.dto;

import kr.gilmok.api.event.entity.Event;
import kr.gilmok.api.event.entity.EventStatus;

import java.time.LocalDateTime;

public record EventResponse(
        Long eventId,
        EventStatus status,
        String name,
        String description,
        LocalDateTime createdAt
) {
    public static EventResponse from(Event event) {
        return new EventResponse(
                event.getId(),
                event.getStatus(),
                event.getName(),
                event.getDescription(),
                event.getCreatedAt()
        );
    }
}
