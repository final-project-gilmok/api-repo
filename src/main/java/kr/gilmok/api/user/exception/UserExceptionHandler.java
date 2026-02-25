package kr.gilmok.api.user.exception;

import kr.gilmok.common.dto.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@Slf4j
@RestControllerAdvice(basePackages = "kr.gilmok.api.user")
public class UserExceptionHandler {

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleUserIdTypeMismatch(MethodArgumentTypeMismatchException e) {
        if ("userId".equals(e.getName())) {
            log.warn("Invalid X-User-Id: {}", e.getValue());
            return ResponseEntity
                    .status(UserErrorCode.INVALID_USER_ID.getHttpStatus())
                    .body(ErrorResponse.of(UserErrorCode.INVALID_USER_ID));
        }
        throw e;
    }
}
