package kr.gilmok.api.queue.exception;

import kr.gilmok.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum QueueErrorCode implements ErrorCode {

    QUEUE_REGISTER_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "Q001", "대기열 등록에 실패했습니다."),
    EVENT_NOT_OPEN(HttpStatus.BAD_REQUEST, "Q002", "현재 이벤트가 오픈되지 않았습니다."),
    INVALID_QUEUE_KEY(HttpStatus.BAD_REQUEST, "Q003", "유효하지 않은 대기열 키입니다."),
    ALREADY_ADMITTED(HttpStatus.CONFLICT, "Q004", "이미 입장된 사용자입니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
