package kr.gilmok.api.event.exception;

import kr.gilmok.common.exception.ErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum EventErrorCode implements ErrorCode {

    EVENT_NOT_FOUND(HttpStatus.NOT_FOUND, "E001", "해당 이벤트를 찾을 수 없습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;

    @Override
    public HttpStatus getHttpStatus() {
        return httpStatus;
    }
}
