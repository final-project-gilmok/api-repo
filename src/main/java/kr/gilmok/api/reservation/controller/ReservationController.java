package kr.gilmok.api.reservation.controller;

import jakarta.validation.Valid;
import kr.gilmok.api.reservation.dto.ReservationCreateRequest;
import kr.gilmok.api.reservation.dto.ReservationResponse;
import kr.gilmok.api.reservation.service.ReservationService;
import kr.gilmok.common.dto.ApiResponse;
import kr.gilmok.common.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/reservations")
@RequiredArgsConstructor
public class ReservationController {

    private final ReservationService reservationService;

    @PostMapping
    public ApiResponse<ReservationResponse> create(
            @AuthenticationPrincipal CustomUserDetails principal,
            @Valid @RequestBody ReservationCreateRequest request) {
        return ApiResponse.success(reservationService.createReservation(principal.user().id(), request));
    }

    @PostMapping("/{code}/confirm")
    public ApiResponse<ReservationResponse> confirm(
            @AuthenticationPrincipal CustomUserDetails principal,
            @PathVariable String code) {
        return ApiResponse.success(reservationService.confirmReservation(principal.user().id(), code));
    }

    @DeleteMapping("/{code}")
    public ApiResponse<ReservationResponse> cancel(
            @AuthenticationPrincipal CustomUserDetails principal,
            @PathVariable String code) {
        return ApiResponse.success(reservationService.cancelReservation(principal.user().id(), code));
    }

    @GetMapping("/{code}")
    public ApiResponse<ReservationResponse> getReservation(
            @AuthenticationPrincipal CustomUserDetails principal,
            @PathVariable String code) {
        return ApiResponse.success(reservationService.getReservation(principal.user().id(), code));
    }

    @GetMapping("/my")
    public ApiResponse<List<ReservationResponse>> getMyReservations(
            @AuthenticationPrincipal CustomUserDetails principal) {
        return ApiResponse.success(reservationService.getMyReservations(principal.user().id()));
    }
}
