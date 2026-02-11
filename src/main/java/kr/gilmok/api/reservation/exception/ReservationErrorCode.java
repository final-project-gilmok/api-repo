package kr.gilmok.api.reservation.exception;

import kr.gilmok.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ReservationErrorCode implements ErrorCode {

    SEAT_NOT_FOUND(HttpStatus.NOT_FOUND, "R001", "좌석 구역을 찾을 수 없습니다."),
    SEAT_NOT_AVAILABLE(HttpStatus.CONFLICT, "R002", "잔여석이 부족합니다."),
    RESERVATION_NOT_FOUND(HttpStatus.NOT_FOUND, "R003", "예약을 찾을 수 없습니다."),
    ALREADY_CONFIRMED(HttpStatus.BAD_REQUEST, "R004", "이미 확정된 예약입니다."),
    ALREADY_CANCELLED(HttpStatus.BAD_REQUEST, "R005", "이미 취소된 예약입니다."),
    NOT_ADMITTED(HttpStatus.FORBIDDEN, "R006", "대기열 통과 상태가 아닙니다."),
    EVENT_NOT_FOUND(HttpStatus.NOT_FOUND, "R007", "이벤트를 찾을 수 없습니다."),
    INVALID_QUANTITY(HttpStatus.BAD_REQUEST, "R008", "예약 수량이 유효하지 않습니다."),
    UNAUTHORIZED(HttpStatus.FORBIDDEN, "R009", "해당 예약에 대한 권한이 없습니다."),
    SEAT_LOCK_FAILED(HttpStatus.CONFLICT, "R010", "좌석 선점에 실패했습니다. 다시 시도해주세요."),
    EVENT_NOT_OPEN(HttpStatus.BAD_REQUEST, "R011", "현재 이벤트가 오픈되지 않았습니다."),
    SEAT_NOT_BELONG_TO_EVENT(HttpStatus.BAD_REQUEST, "R012", "해당 이벤트에 속하지 않는 좌석입니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
