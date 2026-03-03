package kr.gilmok.api.token.service;

import kr.gilmok.api.token.dto.TokenPayload;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TokenService {

    private final JwtProvider jwtProvider;

    @Value("${app.admitted-ttl-seconds}")
    private long admittedTtlSeconds;

    // 대기열 통과자에게 발급할 입장용 토큰 생성
    public String issueAdmissionToken(String eventId, Long userId, String username, long rank) {
        long now = System.currentTimeMillis() / 1000;

        TokenPayload payload = TokenPayload.builder()
                .id(userId)
                .sub(username)
                .status("ADMITTED")
                .role("USER")
                .evt(eventId)
                .rnk(rank)
                .nbf(now)
                .exp(now + admittedTtlSeconds)
                .build();

        return jwtProvider.createToken(payload);
    }
}