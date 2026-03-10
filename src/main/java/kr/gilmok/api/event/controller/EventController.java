package kr.gilmok.api.event.controller;

import jakarta.validation.Valid;
import kr.gilmok.api.event.dto.EventCreateRequest;
import kr.gilmok.api.event.dto.EventResponse;
import kr.gilmok.api.event.service.EventService;
import kr.gilmok.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/events")
@RequiredArgsConstructor
public class EventController {

    private final EventService eventService;

    @GetMapping
    public ApiResponse<List<EventResponse>> list() {
        return ApiResponse.success(eventService.getEvents());
    }

    @PostMapping
    public ApiResponse<EventResponse> create(@Valid @RequestBody EventCreateRequest request) {
        return ApiResponse.success(eventService.createEvent(request));
    }

    @GetMapping("/{eventId}")
    public ApiResponse<EventResponse> getEvent(@PathVariable Long eventId) {
        return ApiResponse.success(eventService.getEvent(eventId));
    }

    @PostMapping("/{eventId}/open")
    public ApiResponse<EventResponse> open(@PathVariable Long eventId) {
        return ApiResponse.success(eventService.openEvent(eventId));
    }

    @PostMapping("/{eventId}/close")
    public ApiResponse<EventResponse> close(@PathVariable Long eventId) {
        return ApiResponse.success(eventService.closeEvent(eventId));
    }
}
