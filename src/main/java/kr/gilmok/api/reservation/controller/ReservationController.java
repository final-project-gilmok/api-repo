package kr.gilmok.api.reservation.controller;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import kr.gilmok.api.queue.service.QueueService;
import kr.gilmok.api.reservation.dto.ReservationCreateRequest;
import kr.gilmok.api.reservation.dto.ReservationResponse;
import kr.gilmok.api.reservation.service.ReservationService;
import kr.gilmok.api.token.service.TokenService;
import kr.gilmok.common.dto.ApiResponse;
import kr.gilmok.common.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/reservations")
@RequiredArgsConstructor
public class ReservationController {

    private final ReservationService reservationService;
    private final QueueService queueService;
    private final TokenService tokenService;

    @Value("${app.admitted-ttl-seconds}")
    private long admittedTtlSeconds;

    @PostMapping
    public ApiResponse<ReservationResponse> create(
            @AuthenticationPrincipal CustomUserDetails principal,
            @Valid @RequestBody ReservationCreateRequest request,
            HttpServletResponse response) {

        // 1. 대기열 검증 (Service 호출 전 혹은 내부에서 수행 가능하지만 명시적으로 분리)
        queueService.verifyQueueAccess(String.valueOf(request.eventId()), request.queueKey(), principal.user().id());

        // 2. 예약 생성 (좌석 선점)
        ReservationResponse res = reservationService.createReservation(principal.user().id(), request);

        // 3. 입장용 토큰 쿠키 발급 (예약 선점 성공 시에만)
        String token = tokenService.issueAdmissionToken(
                String.valueOf(request.eventId()), principal.user().id(), principal.getUsername(), 0L);

        ResponseCookie cookie = createAdmissionCookie(token, admittedTtlSeconds);
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());

        return ApiResponse.success(res);
    }

    @PostMapping("/{code}/confirm")
    public ApiResponse<ReservationResponse> confirm(
            @AuthenticationPrincipal CustomUserDetails principal,
            @PathVariable String code,
            @CookieValue(value = "admissionToken", required = false) String admissionToken,
            HttpServletResponse response) {

        // 1. 예약 확정 (내부에서 토큰 검증 수행)
        ReservationResponse res = reservationService.confirmReservation(principal.user().id(), code, admissionToken);

        // 2. 확정 성공 시 쿠키 만료
        ResponseCookie cookie = createAdmissionCookie("", 0);
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());

        return ApiResponse.success(res);
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

    private ResponseCookie createAdmissionCookie(String value, long maxAgeSeconds) {
        return ResponseCookie.from("admissionToken", value)
                .httpOnly(true)
                .secure(false) // HTTPS 환경이라면 true로 변경 필요
                .path("/")
                .maxAge(maxAgeSeconds)
                .sameSite("Lax")
                .build();
    }
}
