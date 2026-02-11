package kr.gilmok.api.reservation.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ReservationStatus {
    HOLDING("임시 선점"),
    CONFIRMED("예약 확정"),
    CANCELLED("예약 취소");

    private final String description;
}
