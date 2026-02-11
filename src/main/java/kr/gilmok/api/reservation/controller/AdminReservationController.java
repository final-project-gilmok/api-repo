package kr.gilmok.api.reservation.controller;

import jakarta.validation.Valid;
import kr.gilmok.api.reservation.dto.*;
import kr.gilmok.api.reservation.service.ReservationService;
import kr.gilmok.api.reservation.service.SeatService;
import kr.gilmok.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/events/{eventId}")
@RequiredArgsConstructor
public class AdminReservationController {

    private final SeatService seatService;
    private final ReservationService reservationService;

    // --- 좌석 관리 ---

    @PostMapping("/seats")
    public ApiResponse<SeatResponse> createSeat(
            @PathVariable Long eventId,
            @Valid @RequestBody SeatCreateRequest request) {
        return ApiResponse.success(seatService.createSeat(eventId, request));
    }

    @PutMapping("/seats/{seatId}")
    public ApiResponse<SeatResponse> updateSeat(
            @PathVariable Long eventId,
            @PathVariable Long seatId,
            @Valid @RequestBody SeatUpdateRequest request) {
        return ApiResponse.success(seatService.updateSeat(eventId, seatId, request));
    }

    @DeleteMapping("/seats/{seatId}")
    public ApiResponse<Void> deleteSeat(
            @PathVariable Long eventId,
            @PathVariable Long seatId) {
        seatService.deleteSeat(eventId, seatId);
        return ApiResponse.success(null);
    }

    @PostMapping("/seats/init-redis")
    public ApiResponse<Void> initRedis(@PathVariable Long eventId) {
        seatService.initRedisAvailable(eventId);
        return ApiResponse.success(null);
    }

    // --- 예약 현황 ---

    @GetMapping("/reservations")
    public ApiResponse<List<ReservationResponse>> getReservations(@PathVariable Long eventId) {
        return ApiResponse.success(reservationService.getReservationsByEvent(eventId));
    }

    @GetMapping("/reservations/stats")
    public ApiResponse<ReservationStatsResponse> getStats(@PathVariable Long eventId) {
        return ApiResponse.success(reservationService.getReservationStats(eventId));
    }
}
