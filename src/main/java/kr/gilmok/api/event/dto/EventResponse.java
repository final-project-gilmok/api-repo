package kr.gilmok.api.event.dto;

import kr.gilmok.api.event.entity.EventStatus;

public record EventResponse(
        Long eventId,
        EventStatus status
) {}
