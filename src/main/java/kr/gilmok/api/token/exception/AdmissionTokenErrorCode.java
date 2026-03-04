package kr.gilmok.api.token.exception;

import kr.gilmok.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum AdmissionTokenErrorCode implements ErrorCode {
    MISSING_ADMISSION_TOKEN(HttpStatus.UNAUTHORIZED, "T001", "입장용 토큰이 필요합니다."),
    INVALID_ADMISSION_TOKEN(HttpStatus.UNAUTHORIZED, "T002", "입장 토큰이 만료되었거나 유효하지 않습니다. 다시 대기열에 진입해주세요."),
    NOT_ADMITTED_STATUS(HttpStatus.FORBIDDEN, "T003", "올바른 입장 권한이 없습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;

}
