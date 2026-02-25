package kr.gilmok.api.user.service;

import kr.gilmok.api.event.entity.Event;
import kr.gilmok.api.event.entity.EventStatus;
import kr.gilmok.api.event.repository.EventRepository;
import kr.gilmok.api.reservation.entity.Reservation;
import kr.gilmok.api.reservation.entity.Seat;
import kr.gilmok.api.reservation.repository.ReservationRepository;
import kr.gilmok.api.user.dto.UserDashboardResponse;
import kr.gilmok.api.user.dto.UserEventItemResponse;
import kr.gilmok.api.user.dto.UserMeResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserService 단위 테스트")
class UserServiceTest {

    @Mock
    private ReservationRepository reservationRepository;
    @Mock
    private EventRepository eventRepository;

    @InjectMocks
    private UserService userService;

    private void setEntityId(Object entity, Long id) {
        try {
            Field field = entity.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Nested
    @DisplayName("내 정보 조회")
    class GetMe {

        @Test
        @DisplayName("userId와 username으로 UserMeResponse를 반환한다")
        void getMe_returnsResponseWithUsername() {
            Long userId = 1L;
            String username = "testuser";

            UserMeResponse result = userService.getMe(userId, username);

            assertThat(result).isNotNull();
            assertThat(result.userId()).isEqualTo(1L);
            assertThat(result.displayName()).isEqualTo("testuser");
        }

        @Test
        @DisplayName("username이 없으면 userId 기반 displayName을 반환한다")
        void getMe_usernameBlank_usesUserIdAsDisplayName() {
            Long userId = 2L;

            UserMeResponse result = userService.getMe(userId, null);

            assertThat(result.displayName()).isEqualTo("User 2");
        }
    }

    @Nested
    @DisplayName("마이페이지 대시보드")
    class GetDashboard {

        @Test
        @DisplayName("예약 수와 null 대기 이벤트 목록을 반환한다")
        void getDashboard_returnsCountAndNullWaiting() {
            Long userId = 1L;
            when(reservationRepository.countByUserId(userId)).thenReturn(2L);

            UserDashboardResponse result = userService.getDashboard(userId);

            assertThat(result).isNotNull();
            assertThat(result.reservationCount()).isEqualTo(2L);
            assertThat(result.waitingEventIds()).isNull();
            verify(reservationRepository).countByUserId(userId);
        }

        @Test
        @DisplayName("예약이 없으면 0건과 null 대기 목록을 반환한다")
        void getDashboard_noReservations_returnsZeroAndNull() {
            Long userId = 99L;
            when(reservationRepository.countByUserId(userId)).thenReturn(0L);

            UserDashboardResponse result = userService.getDashboard(userId);

            assertThat(result.reservationCount()).isEqualTo(0);
            assertThat(result.waitingEventIds()).isNull();
        }
    }

    @Nested
    @DisplayName("참여 이벤트 목록")
    class GetMyEvents {

        @Test
        @DisplayName("예약한 이벤트를 중복 제거해 반환한다")
        void getMyEvents_returnsDistinctEvents() {
            Long userId = 1L;
            Event event = createEvent(1L);
            when(reservationRepository.findDistinctEventIdsByUserIdOrderByCreatedAtDesc(userId))
                    .thenReturn(List.of(1L));
            when(eventRepository.findAllById(List.of(1L))).thenReturn(List.of(event));

            List<UserEventItemResponse> result = userService.getMyEvents(userId);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).eventId()).isEqualTo(1L);
            assertThat(result.get(0).eventName()).isEqualTo("테스트 이벤트");
            assertThat(result.get(0).status()).isEqualTo(EventStatus.DRAFT);
            verify(reservationRepository).findDistinctEventIdsByUserIdOrderByCreatedAtDesc(userId);
            verify(eventRepository).findAllById(anyList());
        }

        @Test
        @DisplayName("예약이 없으면 빈 목록을 반환한다")
        void getMyEvents_noReservations_returnsEmpty() {
            Long userId = 99L;
            when(reservationRepository.findDistinctEventIdsByUserIdOrderByCreatedAtDesc(userId)).thenReturn(List.of());

            List<UserEventItemResponse> result = userService.getMyEvents(userId);

            assertThat(result).isEmpty();
            verify(reservationRepository).findDistinctEventIdsByUserIdOrderByCreatedAtDesc(userId);
            verify(eventRepository, never()).findAllById(anyList());
        }
    }

    private Event createEvent(Long id) {
        Event event = Event.builder()
                .name("테스트 이벤트")
                .description("설명")
                .startsAt(LocalDateTime.now())
                .endsAt(LocalDateTime.now().plusDays(7))
                .build();
        setEntityId(event, id);
        return event;
    }

    private Seat createSeat(Long id, Event event) {
        Seat seat = Seat.builder()
                .event(event)
                .section("A")
                .totalCount(100)
                .price(50000)
                .build();
        setEntityId(seat, id);
        return seat;
    }

    private Reservation createReservation(Long id, Event event, Seat seat, Long userId) {
        Reservation r = Reservation.builder()
                .event(event)
                .seat(seat)
                .userId(userId)
                .quantity(1)
                .build();
        setEntityId(r, id);
        return r;
    }
}
