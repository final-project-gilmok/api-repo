package kr.gilmok.api.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import kr.gilmok.api.queue.exception.QueueErrorCode;
import kr.gilmok.common.dto.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import io.lettuce.core.RedisCommandTimeoutException;
import org.springframework.dao.QueryTimeoutException;

@Slf4j
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
@ConditionalOnBean(MeterRegistry.class)
public class RedisExceptionHandler {

    private final Counter redisFailureCounter;

    public RedisExceptionHandler(MeterRegistry meterRegistry) {
        this.redisFailureCounter = Counter.builder("queue.redis.failure")
                .description("Number of Redis connection/timeout failures")
                .register(meterRegistry);
    }

    @ExceptionHandler({
            RedisConnectionFailureException.class,
            QueryTimeoutException.class,
            RedisCommandTimeoutException.class
    })
    public ResponseEntity<ErrorResponse> handleRedisException(Exception e) {
        redisFailureCounter.increment();
        log.error("Redis failure: {}", e.getMessage(), e);

        return ResponseEntity
                .status(QueueErrorCode.REDIS_UNAVAILABLE.getHttpStatus())
                .body(ErrorResponse.of(QueueErrorCode.REDIS_UNAVAILABLE));
    }
}
