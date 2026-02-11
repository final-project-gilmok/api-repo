package kr.gilmok.api.reservation.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ReservationCreateRequest(
        @NotNull Long eventId,
        @NotNull Long seatId,
        @Min(1) @Max(4) int quantity,
        @NotBlank String queueKey
) {}
