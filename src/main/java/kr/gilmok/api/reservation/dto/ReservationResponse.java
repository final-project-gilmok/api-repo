package kr.gilmok.api.reservation.dto;

import kr.gilmok.api.reservation.entity.Reservation;
import kr.gilmok.api.reservation.entity.ReservationStatus;

import java.time.LocalDateTime;

public record ReservationResponse(
        Long reservationId,
        String reservationCode,
        Long eventId,
        String eventName,
        Long seatId,
        String section,
        int quantity,
        int price,
        int totalPrice,
        ReservationStatus status,
        Long userId,
        String username,
        LocalDateTime confirmedAt,
        LocalDateTime cancelledAt,
        LocalDateTime createdAt,
        int holdSeconds) {
    public static ReservationResponse from(Reservation r, int holdSeconds) {
        return new ReservationResponse(
                r.getId(),
                r.getReservationCode(),
                r.getEvent().getId(),
                r.getEvent().getName(),
                r.getSeat().getId(),
                r.getSeat().getSection(),
                r.getQuantity(),
                r.getSeat().getPrice(),
                r.getSeat().getPrice() * r.getQuantity(),
                r.getStatus(),
                r.getUserId(),
                r.getUsername(),
                r.getConfirmedAt(),
                r.getCancelledAt(),
                r.getCreatedAt(),
                holdSeconds);
    }
}
