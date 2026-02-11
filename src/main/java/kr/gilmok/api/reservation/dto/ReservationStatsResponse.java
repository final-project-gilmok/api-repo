package kr.gilmok.api.reservation.dto;

public record ReservationStatsResponse(
        Long eventId,
        long holdingCount,
        long confirmedCount,
        long cancelledCount,
        long holdingQuantity,
        long confirmedQuantity,
        long cancelledQuantity
) {}
