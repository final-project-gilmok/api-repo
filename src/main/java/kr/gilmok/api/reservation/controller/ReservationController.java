package kr.gilmok.api.reservation.controller;

import jakarta.validation.Valid;
import kr.gilmok.api.reservation.dto.ReservationCreateRequest;
import kr.gilmok.api.reservation.dto.ReservationResponse;
import kr.gilmok.api.reservation.service.ReservationService;
import kr.gilmok.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/reservations")
@RequiredArgsConstructor
public class ReservationController {

    private final ReservationService reservationService;

    @PostMapping
    public ApiResponse<ReservationResponse> create(
            @RequestHeader("X-User-Id") Long userId,
            @Valid @RequestBody ReservationCreateRequest request) {
        return ApiResponse.success(reservationService.createReservation(userId, request));
    }

    @PostMapping("/{code}/confirm")
    public ApiResponse<ReservationResponse> confirm(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable String code) {
        return ApiResponse.success(reservationService.confirmReservation(userId, code));
    }

    @DeleteMapping("/{code}")
    public ApiResponse<ReservationResponse> cancel(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable String code) {
        return ApiResponse.success(reservationService.cancelReservation(userId, code));
    }

    @GetMapping("/{code}")
    public ApiResponse<ReservationResponse> getReservation(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable String code) {
        return ApiResponse.success(reservationService.getReservation(userId, code));
    }

    @GetMapping("/my")
    public ApiResponse<List<ReservationResponse>> getMyReservations(
            @RequestHeader("X-User-Id") Long userId) {
        return ApiResponse.success(reservationService.getMyReservations(userId));
    }
}
