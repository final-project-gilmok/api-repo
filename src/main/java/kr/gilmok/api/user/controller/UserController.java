package kr.gilmok.api.user.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import kr.gilmok.api.user.dto.UserDashboardResponse;
import kr.gilmok.api.user.dto.UserEventItemResponse;
import kr.gilmok.api.user.dto.UserMeResponse;
import kr.gilmok.api.user.service.UserService;
import kr.gilmok.common.dto.ApiResponse;
import kr.gilmok.common.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
@Tag(name = "User", description = "사용자 정보 조회 API")
@SecurityRequirement(name = "bearerAuth")
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    @Operation(summary = "내 정보 조회", description = "로그인한 사용자의 기본 정보를 조회합니다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패")
    })
    public ApiResponse<UserMeResponse> getMe(@AuthenticationPrincipal CustomUserDetails principal) {
        return ApiResponse.success(userService.getMe(principal.user().id(), principal.user().username()));
    }

    @GetMapping("/me/dashboard")
    @Operation(summary = "내 대시보드 조회", description = "로그인한 사용자의 예약/대기열 요약 정보를 조회합니다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패")
    })
    public ApiResponse<UserDashboardResponse> getDashboard(@AuthenticationPrincipal CustomUserDetails principal) {
        return ApiResponse.success(userService.getDashboard(userId(principal)));
    }

    @GetMapping("/me/events")
    @Operation(summary = "내 이벤트 목록 조회", description = "로그인한 사용자의 이벤트 참여 목록을 조회합니다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패")
    })
    public ApiResponse<List<UserEventItemResponse>> getMyEvents(@AuthenticationPrincipal CustomUserDetails principal) {
        return ApiResponse.success(userService.getMyEvents(userId(principal)));
    }

    private static Long userId(CustomUserDetails principal) {
        return principal.user().id();
    }
}
