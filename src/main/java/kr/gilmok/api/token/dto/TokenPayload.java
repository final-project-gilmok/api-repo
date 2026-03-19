package kr.gilmok.api.token.dto;

import lombok.Builder;

@Builder
public record TokenPayload(
        String jti,  // JWT ID (One-Time Token 패턴용, 예약 확정 후 무효화에 사용)
        Long id, // common filter용 (userId)
        String sub, // spec (user_unique_id)
        String status, // common filter용 (대기열 통과자는 ADMITTED)
        String role, // common filter용 (USER)
        String evt, // spec (event_id)
        String res, // spec (reservation_code)
        Long rnk, // spec (당시 대기 순번)
        Long nbf, // spec (입장 허용 시작 시간, epoch seconds)
        Long exp // spec (만료 시간, epoch seconds)
) {
}