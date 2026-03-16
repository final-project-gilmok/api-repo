package kr.gilmok.api.token.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import kr.gilmok.api.token.dto.TokenPayload;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtProvider {

    private final SecretKey key;

    public JwtProvider(@Value("${app.jwt.secret}") String secret) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    // Payload 데이터를 받아 JWT 문자열로 생성 (jti 포함)
    public String createToken(TokenPayload payload) {
        var builder = Jwts.builder()
                .setSubject(payload.sub())
                .claim("id", payload.id())
                .claim("status", payload.status())
                .claim("role", payload.role())
                .claim("evt", payload.evt())
                .claim("res", payload.res())
                .claim("rnk", payload.rnk())
                // nbf와 exp는 초 단위(epoch)이므로 밀리초(*1000)로 변환해 Date 객체로 설정
                .setNotBefore(new Date(payload.nbf() * 1000))
                .setExpiration(new Date(payload.exp() * 1000))
                .signWith(key, SignatureAlgorithm.HS256);

        // jti가 있는 경우에만 설정 (One-Time Token 패턴)
        if (payload.jti() != null) {
            builder.setId(payload.jti());
        }

        return builder.compact();
    }

    // 토큰의 서명 및 만료 여부 검증
    public boolean validateToken(String token) {
        try {
            Jwts.parserBuilder()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // 검증을 통과한 토큰에서 데이터(Claims) 추출
    public Claims getClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    // 토큰에서 jti(JWT ID)를 추출
    // Admission Token One-Time 패턴에서 사용: 예약 확정 후 해당 jti를 blacklist에 등록
    public String getJti(String token) {
        return getClaims(token).getId();
    }

    // 토큰의 남은 유효시간(초)을 반환
    // blacklist TTL 설정에 사용
    public long getRemainingTtlSeconds(String token) {
        Date expiration = getClaims(token).getExpiration();
        long remainingMs = expiration.getTime() - System.currentTimeMillis();
        if (remainingMs <= 0) return 0L;
        return Math.max((remainingMs + 999L) / 1000L, 1L);
    }
}