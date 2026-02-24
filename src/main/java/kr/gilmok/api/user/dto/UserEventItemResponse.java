package kr.gilmok.api.user.dto;

import kr.gilmok.api.event.entity.EventStatus;

public record UserEventItemResponse(
        Long eventId,
        String eventName,
        EventStatus status
) {
}
