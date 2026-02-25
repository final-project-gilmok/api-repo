package kr.gilmok.api.user.controller;

import kr.gilmok.api.user.dto.UserDashboardResponse;
import kr.gilmok.api.user.dto.UserEventItemResponse;
import kr.gilmok.api.user.dto.UserMeResponse;
import kr.gilmok.api.user.exception.UserErrorCode;
import kr.gilmok.api.user.service.UserService;
import kr.gilmok.common.dto.ApiResponse;
import kr.gilmok.common.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// TODO: JWT 인증 적용 후 userId 추출로 변경.

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    public ApiResponse<UserMeResponse> getMe(@RequestHeader(value = "X-User-Id", required = false) Long userId) {
        requireUserId(userId);
        return ApiResponse.success(userService.getMe(userId));
    }

    @GetMapping("/me/dashboard")
    public ApiResponse<UserDashboardResponse> getDashboard(@RequestHeader(value = "X-User-Id", required = false) Long userId) {
        requireUserId(userId);
        return ApiResponse.success(userService.getDashboard(userId));
    }

    @GetMapping("/me/events")
    public ApiResponse<List<UserEventItemResponse>> getMyEvents(@RequestHeader(value = "X-User-Id", required = false) Long userId) {
        requireUserId(userId);
        return ApiResponse.success(userService.getMyEvents(userId));
    }

    private void requireUserId(Long userId) {
        if (userId == null) {
            throw new CustomException(UserErrorCode.MISSING_USER_ID);
        }
    }
}
