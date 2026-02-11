package kr.gilmok.api.policy.exception;

import kr.gilmok.common.exception.ErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum PolicyErrorCode implements ErrorCode {

    POLICY_NOT_FOUND(HttpStatus.NOT_FOUND, "P001", "해당 정책이 존재하지 않습니다."),
    POLICY_CONFLICT(HttpStatus.CONFLICT, "P002", "다른 사용자가 정책을 수정했습니다. 최신 내용을 확인한 뒤 다시 시도해 주세요.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;

    @Override
    public HttpStatus getHttpStatus() {
        return httpStatus;
    }
}
