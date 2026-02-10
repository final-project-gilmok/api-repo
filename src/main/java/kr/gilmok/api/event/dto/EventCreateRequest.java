package kr.gilmok.api.event.dto;

import java.time.LocalDateTime;

public record EventCreateRequest(
        String name,
        String description,
        LocalDateTime startsAt,
        LocalDateTime endsAt,
        String demoUrl
) {}
