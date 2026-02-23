package kr.gilmok.api.event.service;

import kr.gilmok.api.event.dto.EventCreateRequest;
import kr.gilmok.api.event.dto.EventListResponse;
import kr.gilmok.api.event.dto.EventResponse;
import kr.gilmok.api.event.entity.Event;
import kr.gilmok.api.event.entity.EventStatus;
import kr.gilmok.api.event.repository.EventRepository;
import kr.gilmok.api.event.exception.EventErrorCode;
import kr.gilmok.api.policy.service.PolicyService;
import kr.gilmok.common.dto.ApiResponse;
import kr.gilmok.common.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EventService {

    private final EventRepository eventRepository;
    private final PolicyService policyService;

    @Transactional
    public ApiResponse<EventResponse> createEvent(EventCreateRequest dto) {
        Event event = Event.builder()
                .name(dto.name())
                .description(dto.description())
                .startsAt(dto.startsAt())
                .endsAt(dto.endsAt())
                .build();

        Event saved = eventRepository.save(event);
        policyService.createPolicyForEvent(saved.getId(), dto.policy());
        return ApiResponse.success(EventResponse.from(saved));
    }

    @Transactional
    public ApiResponse<EventResponse> openEvent(Long eventId) {
        Event event = findEventById(eventId);
        event.open(); // 엔티티 내부 비즈니스 메서드 호출
        return ApiResponse.success(EventResponse.from(event));
    }

    @Transactional
    public ApiResponse<EventResponse> closeEvent(Long eventId) {
        Event event = findEventById(eventId);
        event.close();
        return ApiResponse.success(EventResponse.from(event));
    }

    public List<EventListResponse> getOpenEvents() {
        return eventRepository.findByStatusOrderByStartsAtDesc(EventStatus.OPEN)
                .stream()
                .map(EventListResponse::from)
                .toList();
    }

    public ApiResponse<EventResponse> getEvent(Long eventId) {
        Event event = findEventById(eventId);
        return ApiResponse.success(EventResponse.from(event));
    }

    public ApiResponse<List<EventResponse>> getEvents() {
        List<EventResponse> list = eventRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(EventResponse::from)
                .toList();
        return ApiResponse.success(list);
    }

    private Event findEventById(Long eventId) {
        return eventRepository.findById(eventId)
                .orElseThrow(() -> new CustomException(EventErrorCode.EVENT_NOT_FOUND));
    }
}
