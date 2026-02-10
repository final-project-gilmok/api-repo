package kr.gilmok.api.event.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public record EventCreateRequest(
        @NotBlank String name,
        @NotBlank String description,
        @NotNull LocalDateTime startsAt,
        @NotNull LocalDateTime endsAt,
        String demoUrl
) {}
