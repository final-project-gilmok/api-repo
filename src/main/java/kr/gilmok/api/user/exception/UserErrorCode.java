package kr.gilmok.api.user.exception;

import kr.gilmok.common.exception.ErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum UserErrorCode implements ErrorCode {

    MISSING_USER_ID(HttpStatus.BAD_REQUEST, "U002", "X-User-Id 헤더가 없습니다."),
    INVALID_USER_ID(HttpStatus.BAD_REQUEST, "U003", "X-User-Id 값 형식이 올바르지 않습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
