package kr.gilmok.api.user.dto;

import java.time.LocalDateTime;


 // TODO: auth-repo 연동 또는 User 캐시 조회로 교체.

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
