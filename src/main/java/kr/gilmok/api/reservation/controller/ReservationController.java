package kr.gilmok.api.reservation.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Reservation", description = "사용자 예약 API")
@SecurityRequirement(name = "bearerAuth")
public class ReservationController {

    private final ReservationService reservationService;
    private final QueueService queueService;
    private final TokenService tokenService;

    @Value("${app.admitted-ttl-seconds}")
    private long admittedTtlSeconds;

    @PostMapping
    @Operation(summary = "예약 생성", description = "대기열 검증 후 좌석 예약을 생성하고 입장 토큰 쿠키를 발급합니다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "생성 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "요청 값 검증 실패"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "대기열 검증 실패(NOT_ADMITTED)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "이벤트 또는 좌석 없음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "좌석 충돌 또는 상태 충돌")
    })
    public ApiResponse<ReservationResponse> create(
            @AuthenticationPrincipal CustomUserDetails principal,
            @Valid @RequestBody ReservationCreateRequest request,
            HttpServletResponse response) {

        // 1. 대기열 검증 (Service 호출 전 혹은 내부에서 수행 가능하지만 명시적으로 분리)
        queueService.verifyQueueAccess(String.valueOf(request.eventId()), request.queueKey(), principal.user().id());

        // 2. 예약 생성 (좌석 선점)
        ReservationResponse res = reservationService.createReservation(principal.user().id(), principal.getUsername(), request);

        // 3. 입장용 토큰 쿠키 발급 (예약 선점 성공 시에만)
        String token = tokenService.issueAdmissionToken(
                String.valueOf(request.eventId()), res.reservationCode(), principal.user().id(),
                principal.getUsername(), 0L);

        ResponseCookie cookie = createAdmissionCookie("admissionToken_" + res.reservationCode(), token,
                admittedTtlSeconds, "/");
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());

        return ApiResponse.success(res);
    }

    @PostMapping("/{code}/confirm")
    @Operation(summary = "예약 확정", description = "입장 토큰 검증 후 예약을 확정하고 입장 토큰 쿠키를 만료 처리합니다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "확정 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "다른 사용자의 예약 확정 시도"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "예약 없음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "상태 충돌")
    })
    public ApiResponse<ReservationResponse> confirm(
            @AuthenticationPrincipal CustomUserDetails principal,
            @PathVariable String code,
            @RequestAttribute(value = "admissionToken") String admissionToken,
            HttpServletResponse response) {

        // 1. 예약 확정 (내부에서 토큰 검증 수행)
        ReservationResponse res = reservationService.confirmReservation(principal.user().id(), code, admissionToken);

        // 2. 확정 성공 시 쿠키 만료
        ResponseCookie cookie = createAdmissionCookie("admissionToken_" + code, "", 0, "/");
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());

        return ApiResponse.success(res);
    }

    @DeleteMapping("/{code}")
    @Operation(summary = "예약 취소", description = "예약 코드를 기준으로 예약을 취소합니다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "취소 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "다른 사용자의 예약 취소 시도"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "예약 없음")
    })
    public ApiResponse<ReservationResponse> cancel(
            @AuthenticationPrincipal CustomUserDetails principal,
            @PathVariable String code) {
        return ApiResponse.success(reservationService.cancelReservation(principal.user().id(), code));
    }

    @GetMapping("/{code}")
    @Operation(summary = "예약 단건 조회", description = "예약 코드를 기준으로 예약 상세를 조회합니다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "다른 사용자의 예약 접근 시도"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "예약 없음")
    })
    public ApiResponse<ReservationResponse> getReservation(
            @AuthenticationPrincipal CustomUserDetails principal,
            @PathVariable String code) {
        return ApiResponse.success(reservationService.getReservation(principal.user().id(), code));
    }

    @GetMapping("/my")
    @Operation(summary = "내 예약 목록 조회", description = "로그인한 사용자의 예약 목록을 조회합니다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패")
    })
    public ApiResponse<List<ReservationResponse>> getMyReservations(
            @AuthenticationPrincipal CustomUserDetails principal) {
        return ApiResponse.success(reservationService.getMyReservations(principal.user().id()));
    }

    private ResponseCookie createAdmissionCookie(String name, String value, long maxAgeSeconds, String path) {
        return ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(false)
                .path(path)
                .maxAge(maxAgeSeconds)
                .sameSite("Lax")
                .build();
    }
}
