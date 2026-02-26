package kr.gilmok.api.user.dto;

public record UserMeResponse(
        Long userId,
        String displayName
) {
    public static UserMeResponse of(Long userId, String username) {
        String normalized = (username == null ? null : username.trim());
        String displayName = (normalized != null && !normalized.isBlank()) ? normalized : "User " + userId;
        return new UserMeResponse(userId, displayName);
    }
}
