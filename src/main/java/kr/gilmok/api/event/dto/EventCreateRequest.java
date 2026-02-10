package kr.gilmok.api.event.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public record EventCreateRequest(
        @NotBlank String name,
        @NotBlank String description,
        @NotNull LocalDateTime startsAt,
        @NotNull LocalDateTime endsAt,
        String demoUrl
) {
    @AssertTrue(message = "endsAt must be after startsAt")
    public boolean isValidPeriod() {
        return startsAt != null && endsAt != null && endsAt.isAfter(startsAt);
    }
}
