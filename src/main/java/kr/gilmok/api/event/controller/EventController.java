package kr.gilmok.api.event.controller;

import jakarta.validation.Valid;
import kr.gilmok.api.event.dto.EventCreateRequest;
import kr.gilmok.api.event.dto.EventResponse;
import kr.gilmok.api.event.service.EventService;
import kr.gilmok.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/events")
@RequiredArgsConstructor
public class EventController {

    private final EventService eventService;

    @PostMapping
    public ApiResponse<EventResponse> create(@Valid @RequestBody EventCreateRequest request) {
        return eventService.createEvent(request);
    }

    @GetMapping("/{eventId}")
    public ApiResponse<EventResponse> getEvent(@PathVariable Long eventId) {
        return eventService.getEvent(eventId);
    }

    @PostMapping("/{eventId}/open")
    public ApiResponse<EventResponse> open(@PathVariable Long eventId) {
        return eventService.openEvent(eventId);
    }

    @PostMapping("/{eventId}/close")
    public ApiResponse<EventResponse> close(@PathVariable Long eventId) {
        return eventService.closeEvent(eventId);
    }
}
