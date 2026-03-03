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

    // Payload 데이터를 받아 JWT 문자열로 생성
    public String createToken(TokenPayload payload) {
        return Jwts.builder()
                .setSubject(payload.sub())
                .claim("id", payload.id())
                .claim("status", payload.status())
                .claim("role", payload.role())
                .claim("evt", payload.evt())
                .claim("rnk", payload.rnk())
                // nbf와 exp는 초 단위(epoch)이므로 밀리초(*1000)로 변환해 Date 객체로 설정
                .setNotBefore(new Date(payload.nbf() * 1000))
                .setExpiration(new Date(payload.exp() * 1000))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
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
}