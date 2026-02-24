package kr.gilmok.api.user.service;

import kr.gilmok.api.event.entity.Event;
import kr.gilmok.api.event.repository.EventRepository;
import kr.gilmok.api.reservation.dto.ReservationResponse;
import kr.gilmok.api.reservation.repository.ReservationRepository;
import kr.gilmok.api.user.dto.UserDashboardResponse;
import kr.gilmok.api.user.dto.UserEventItemResponse;
import kr.gilmok.api.user.dto.UserMeResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserService {

    private static final int RECENT_RESERVATIONS_LIMIT = 10;

    private final ReservationRepository reservationRepository;
    private final EventRepository eventRepository;

    @Transactional(readOnly = true)
    public UserMeResponse getMe(Long userId) {
        return UserMeResponse.of(userId);
    }

    @Transactional(readOnly = true)
    public UserDashboardResponse getDashboard(Long userId) {
        long count = reservationRepository.countByUserId(userId);
        List<ReservationResponse> recent = reservationRepository
                .findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .limit(RECENT_RESERVATIONS_LIMIT)
                .map(ReservationResponse::from)
                .toList();
        List<Long> waitingEventIds = List.of(); // TODO: queue 연동 시 대기 중인 eventId 목록
        return new UserDashboardResponse((int) count, recent, waitingEventIds);
    }

    @Transactional(readOnly = true)
    public List<UserEventItemResponse> getMyEvents(Long userId) {
        Set<Long> eventIds = reservationRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(r -> r.getEvent().getId())
                .collect(Collectors.toSet());
        if (eventIds.isEmpty()) {
            return List.of();
        }
        List<Event> events = eventRepository.findAllById(eventIds);
        return events.stream()
                .map(e -> new UserEventItemResponse(e.getId(), e.getName(), e.getStatus()))
                .toList();
    }
}
