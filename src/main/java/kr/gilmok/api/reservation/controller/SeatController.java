package kr.gilmok.api.reservation.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import kr.gilmok.api.event.dto.EventListResponse;
import kr.gilmok.api.event.service.EventService;
import kr.gilmok.api.reservation.dto.SeatResponse;
import kr.gilmok.api.reservation.service.SeatService;
import kr.gilmok.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@Tag(name = "Seat", description = "이벤트 및 좌석 조회 API")
public class SeatController {

    private final SeatService seatService;
    private final EventService eventService;

    @GetMapping("/events")
    @Operation(summary = "오픈 이벤트 목록 조회", description = "현재 오픈 상태인 이벤트 목록을 조회합니다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공")
    })
    public ApiResponse<List<EventListResponse>> getOpenEvents() {
        return ApiResponse.success(eventService.getOpenEvents());
    }

    @GetMapping("/events/{eventId}/seats")
    @Operation(summary = "좌석 목록 조회", description = "eventId에 해당하는 좌석 목록을 조회합니다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "이벤트 없음")
    })
    public ApiResponse<List<SeatResponse>> getSeats(@PathVariable Long eventId) {
        return ApiResponse.success(seatService.getSeatsByEvent(eventId));
    }
}
