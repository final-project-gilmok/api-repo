package kr.gilmok.api.reservation.controller;

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
public class SeatController {

    private final SeatService seatService;
    private final EventService eventService;

    @GetMapping("/events")
    public ApiResponse<List<EventListResponse>> getOpenEvents() {
        return ApiResponse.success(eventService.getOpenEvents());
    }

    @GetMapping("/events/{eventId}/seats")
    public ApiResponse<List<SeatResponse>> getSeats(@PathVariable Long eventId) {
        return ApiResponse.success(seatService.getSeatsByEvent(eventId));
    }
}
