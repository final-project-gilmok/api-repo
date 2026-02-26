package kr.gilmok.api.user.controller;

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
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    public ApiResponse<UserMeResponse> getMe(@AuthenticationPrincipal CustomUserDetails principal) {
        return ApiResponse.success(userService.getMe(principal.user().id(), principal.user().username()));
    }

    @GetMapping("/me/dashboard")
    public ApiResponse<UserDashboardResponse> getDashboard(@AuthenticationPrincipal CustomUserDetails principal) {
        return ApiResponse.success(userService.getDashboard(userId(principal)));
    }

    @GetMapping("/me/events")
    public ApiResponse<List<UserEventItemResponse>> getMyEvents(@AuthenticationPrincipal CustomUserDetails principal) {
        return ApiResponse.success(userService.getMyEvents(userId(principal)));
    }

    private static Long userId(CustomUserDetails principal) {
        return principal.user().id();
    }
}
