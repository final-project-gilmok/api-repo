package kr.gilmok.api.user.dto;

import kr.gilmok.api.reservation.dto.ReservationResponse;

import java.util.List;

public record UserDashboardResponse(
        int reservationCount,
        List<ReservationResponse> recentReservations,
        List<Long> waitingEventIds
) {
}
