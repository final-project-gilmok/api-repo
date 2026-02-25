package kr.gilmok.api.user.dto;

public record UserMeResponse(
        Long userId,
        String displayName
) {
    public static UserMeResponse of(Long userId, String username) {
        return new UserMeResponse(
                userId,
                username != null && !username.isBlank() ? username : "User " + userId
        );
    }
}
