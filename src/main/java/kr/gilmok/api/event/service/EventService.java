package kr.gilmok.api.event.service;

import kr.gilmok.api.event.dto.EventCreateRequest;
import kr.gilmok.api.event.dto.EventResponse;
import kr.gilmok.api.event.entity.Event;
import kr.gilmok.api.event.repository.EventRepository;
import kr.gilmok.api.event.exception.EventErrorCode;
import kr.gilmok.common.dto.ApiResponse;
import kr.gilmok.common.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EventService {

    private final EventRepository eventRepository;

    @Transactional
    public ApiResponse<EventResponse> createEvent(EventCreateRequest dto) {
        Event event = Event.builder()
                .name(dto.name())
                .description(dto.description())
                .startsAt(dto.startsAt())
                .endsAt(dto.endsAt())
                .demoUrl(dto.demoUrl())
                .build();

        Event saved = eventRepository.save(event);
        return ApiResponse.success(new EventResponse(saved.getId(), saved.getStatus()));
    }

    @Transactional
    public ApiResponse<EventResponse> openEvent(Long eventId) {
        Event event = findEventById(eventId);
        event.open(); // 엔티티 내부 비즈니스 메서드 호출
        return ApiResponse.success(new EventResponse(event.getId(), event.getStatus()));
    }

    @Transactional
    public ApiResponse<EventResponse> closeEvent(Long eventId) {
        Event event = findEventById(eventId);
        event.close();
        return ApiResponse.success(new EventResponse(event.getId(), event.getStatus()));
    }

    public ApiResponse<EventResponse> getEvent(Long eventId) {
        Event event = findEventById(eventId);
        return ApiResponse.success(new EventResponse(event.getId(), event.getStatus()));
    }

    private Event findEventById(Long eventId) {
        return eventRepository.findById(eventId)
                .orElseThrow(() -> new CustomException(EventErrorCode.EVENT_NOT_FOUND));
    }
}
