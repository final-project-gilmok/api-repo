package kr.gilmok.api.ai.entity;

import kr.gilmok.common.exception.ErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum AiErrorCode implements ErrorCode {

    /**
     * AI 추천 관련 (AI)
     */
    AI_RECOMMENDATION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "AI001", "AI 정책 추천에 실패했습니다."),
    AI_SERIALIZATION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "AI002", "AI 추천 결과 저장에 실패했습니다."),
    AI_METRICS_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "AI003", "메트릭 데이터를 가져올 수 없습니다."),
    AI_EVENT_NOT_FOUND(HttpStatus.NOT_FOUND, "AI004", "AI 추천 대상 이벤트를 찾을 수 없습니다."),
    AI_DB_SAVE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "AI005", "AI 추천 이력 저장에 실패했습니다."), // ← 추가
    AI_INVALID_SERVER_SPEC_PARTIAL(HttpStatus.BAD_REQUEST, "AI006", "서버 스펙은 전부 입력하거나 전부 비워주세요."),
    AI_INVALID_SERVER_SPEC_VALUE(HttpStatus.BAD_REQUEST, "AI007", "서버 스펙 값은 1 이상이어야 합니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;

    @Override
    public HttpStatus getHttpStatus() {
        return httpStatus;
    }
}
