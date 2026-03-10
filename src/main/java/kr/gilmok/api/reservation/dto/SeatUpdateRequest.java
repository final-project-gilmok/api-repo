package kr.gilmok.api.reservation.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record SeatUpdateRequest(
        @NotBlank String section,
        @Min(1) int totalCount,
        @Min(0) int price
) {}
