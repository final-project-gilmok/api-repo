package kr.gilmok.api.reservation.dto;

public record SeatStatsResponse(
        Long seatId,
        String section,
        int totalCount,
        int reservedCount,
        int availableCount,
        int price
) {}
