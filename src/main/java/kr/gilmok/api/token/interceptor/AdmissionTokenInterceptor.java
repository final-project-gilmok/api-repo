package kr.gilmok.api.token.interceptor;

import io.jsonwebtoken.Claims;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import kr.gilmok.api.token.exception.AdmissionTokenErrorCode;
import kr.gilmok.api.token.service.JwtProvider;
import kr.gilmok.common.exception.CustomException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Arrays;

@Slf4j
@Component
@RequiredArgsConstructor
public class AdmissionTokenInterceptor implements HandlerInterceptor {

    private final JwtProvider jwtProvider;

    private static final String ADMITTED_STATUS = "ADMITTED";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {

        // CORS 통신을 위한 OPTIONS 요청은 토큰 검증 없이 통과
        if (HttpMethod.OPTIONS.name().equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        // 입장용 토큰 추출
        String admissionToken = resolveToken(request);

        if (admissionToken == null || admissionToken.trim().isEmpty()) {
            log.warn("[AdmissionInterceptor] 입장용 토큰 누락 - URI: {}", request.getRequestURI());
            throw new CustomException(AdmissionTokenErrorCode.MISSING_ADMISSION_TOKEN);
        }

        // 토큰 유효성 및 만료(5분) 여부 검증
        if (!jwtProvider.validateToken(admissionToken)) {
            log.warn("[AdmissionInterceptor] 유효하지 않거나 만료된 토큰");
            throw new CustomException(AdmissionTokenErrorCode.INVALID_ADMISSION_TOKEN);
        }

        // 토큰 내부 클레임(데이터) 확인
        Claims claims = jwtProvider.getClaims(admissionToken);
        String status = claims.get("status", String.class);

        // 상태값이 "ADMITTED"인지 확인
        if (!ADMITTED_STATUS.equals(status)) {
            log.warn("[AdmissionInterceptor] 인가되지 않은 토큰 상태: {}", status);
            throw new CustomException(AdmissionTokenErrorCode.NOT_ADMITTED_STATUS);
        }

        // 다음 필터/컨트롤러에서 사용할 수 있도록 검증된 데이터를 Request에 담아줌
        request.setAttribute("admittedEventId", claims.get("evt"));
        request.setAttribute("admittedUserId", claims.get("sub"));

        log.info("[AdmissionInterceptor] 입장 통과 - eventId: {}, userId: {}", claims.get("evt"), claims.get("sub"));

        return true;
    }

    private String resolveToken(HttpServletRequest request) {
        if (request.getCookies() != null) {
            return Arrays.stream(request.getCookies())
                    .filter(c -> "admissionToken".equals(c.getName()))
                    .map(Cookie::getValue)
                    .findFirst()
                    .orElse(null);
        }
        return null;
    }
}