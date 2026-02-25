package kr.gilmok.api.user.dto;

import java.time.LocalDateTime;

/**
 * GET /users/me 응답. 로그인한 유저의 표시용 정보.
 * WIP: auth-repo 연동 또는 User 캐시 조회 전까지는 스텁 데이터(displayName, joinedAt)를 반환합니다.
 */
public record UserMeResponse(
        Long userId,
        String displayName,
        LocalDateTime joinedAt
) {
    public static UserMeResponse of(Long userId) {
        return new UserMeResponse(
                userId,
                "User " + userId,
                null
        );
    }
}
