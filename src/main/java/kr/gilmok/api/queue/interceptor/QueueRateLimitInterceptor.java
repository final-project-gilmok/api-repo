package kr.gilmok.api.queue.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import kr.gilmok.api.queue.exception.QueueErrorCode;
import kr.gilmok.common.dto.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Collections;
import java.util.UUID;

@Slf4j
@Component
public class QueueRateLimitInterceptor implements HandlerInterceptor {

    private final RedisTemplate<String, String> redisTemplate;
    private final DefaultRedisScript<Long> rateLimitScript;
    private final ObjectMapper objectMapper;

    @Value("${queue.rate-limit.requests-per-second:10}")
    private int requestsPerSecond;

    @Value("${queue.rate-limit.window-ms:1000}")
    private long windowMs;

    public QueueRateLimitInterceptor(
            RedisTemplate<String, String> redisTemplate,
            DefaultRedisScript<Long> rateLimitScript,
            ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.rateLimitScript = rateLimitScript;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String clientIp = resolveClientIp(request);
        String key = "rate:queue:" + clientIp;

        try {
            Long result = redisTemplate.execute(
                    rateLimitScript,
                    Collections.singletonList(key),
                    String.valueOf(requestsPerSecond),
                    String.valueOf(windowMs),
                    String.valueOf(System.currentTimeMillis()),
                    UUID.randomUUID().toString()
            );

            if (result != null && result == 0) {
                log.warn("Rate limit exceeded: ip={}, path={}", clientIp, request.getRequestURI());
                writeErrorResponse(response);
                return false;
            }
        } catch (RedisConnectionFailureException e) {
            log.warn("Rate limit check failed - Redis connection failure (fail-open): ip={}", clientIp);
        } catch (Exception e) {
            log.warn("Rate limit check failed (fail-open): ip={}, error={}, type={}",
                    clientIp, e.getMessage(), e.getClass().getSimpleName());
        }

        return true;
    }

    private String resolveClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private void writeErrorResponse(HttpServletResponse response) throws Exception {
        QueueErrorCode errorCode = QueueErrorCode.RATE_LIMITED;
        response.setStatus(errorCode.getHttpStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(ErrorResponse.of(errorCode)));
    }
}
