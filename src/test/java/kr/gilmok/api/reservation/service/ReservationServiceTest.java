package kr.gilmok.api.reservation.service;

import io.jsonwebtoken.Claims;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import kr.gilmok.api.event.entity.Event;
import kr.gilmok.api.event.repository.EventRepository;
import kr.gilmok.api.queue.repository.QueueRedisRepository;
import kr.gilmok.api.reservation.dto.ReservationCreateRequest;
import kr.gilmok.api.reservation.dto.ReservationResponse;
import kr.gilmok.api.reservation.entity.Reservation;
import kr.gilmok.api.reservation.entity.ReservationStatus;
import kr.gilmok.api.reservation.entity.Seat;
import kr.gilmok.api.reservation.exception.ReservationErrorCode;
import kr.gilmok.api.reservation.repository.ReservationRepository;
import kr.gilmok.api.reservation.repository.SeatLockRedisRepository;
import kr.gilmok.api.reservation.repository.SeatRepository;
import kr.gilmok.api.token.service.JwtProvider;
import kr.gilmok.common.exception.CustomException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReservationService 단위 테스트")
class ReservationServiceTest {

    @Mock
    private ReservationRepository reservationRepository;
    @Mock
    private SeatRepository seatRepository;
    @Mock
    private EventRepository eventRepository;
    @Mock
    private SeatLockRedisRepository seatLockRedisRepository;
    @Mock
    private QueueRedisRepository queueRedisRepository;
    @Mock
    private MeterRegistry meterRegistry;
    @Mock
    private JwtProvider jwtProvider;

    @InjectMocks
    private ReservationService reservationService;

    @BeforeEach
    void setUp() {
        setField(reservationService, "seatLockTtlSeconds", 300);
        setField(reservationService, "maxQuantity", 4);
    }

    private Event createEvent(Long id) {
        Event event = Event.builder()
                .name("테스트 이벤트")
                .description("설명")
                .startsAt(LocalDateTime.now())
                .endsAt(LocalDateTime.now().plusDays(7))
                .build();
        setField(event, "id", id);
        event.open(); // 피드백: isOpen() 검증을 위해 OPEN 상태로
        return event;
    }

    private Seat createSeat(Long id, Event event) {
        Seat seat = Seat.builder()
                .event(event)
                .section("VIP")
                .totalCount(100)
                .price(150000)
                .build();
        setField(seat, "id", id);
        return seat;
    }

    private Reservation createReservation(Long id, Event event, Seat seat, Long userId) {
        Reservation reservation = Reservation.builder()
                .event(event)
                .seat(seat)
                .userId(userId)
                .quantity(2)
                .build();
        setField(reservation, "id", id);
        return reservation;
    }

    private static void setField(Object target, String fieldName, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Nested
    @DisplayName("예약 생성")
    class CreateReservation {

        @Test
        @DisplayName("대기열 통과 + 이벤트 OPEN + 잔여석 있으면 HOLDING 예약이 생성된다")
        void create_success() {
            // given
            Long userId = 1L;
            Event event = createEvent(1L);
            Seat seat = createSeat(10L, event);
            ReservationCreateRequest request = new ReservationCreateRequest(1L, 10L, 2, "queue-key-1");

            when(queueRedisRepository.isAdmitted("1", "queue-key-1")).thenReturn(true);
            when(eventRepository.findById(1L)).thenReturn(Optional.of(event));
            when(seatRepository.findById(10L)).thenReturn(Optional.of(seat));
            when(seatLockRedisRepository.lock(eq(1L), eq(10L), eq(userId), eq(2), anyInt())).thenReturn(true);
            when(reservationRepository.save(any(Reservation.class))).thenAnswer(inv -> inv.getArgument(0));

            // when
            ReservationResponse response = reservationService.createReservation(userId, request);

            // then
            assertThat(response.status()).isEqualTo(ReservationStatus.HOLDING);
            assertThat(response.quantity()).isEqualTo(2);
            assertThat(response.reservationCode()).isNotNull();
            verify(seatLockRedisRepository).lock(eq(1L), eq(10L), eq(userId), eq(2), anyInt());
        }

        @Test
        @DisplayName("대기열 미통과 시 NOT_ADMITTED 예외가 발생한다")
        void create_notAdmitted_throwsException() {
            // given
            Long userId = 1L;
            ReservationCreateRequest request = new ReservationCreateRequest(1L, 10L, 2, "queue-key-1");
            when(queueRedisRepository.isAdmitted("1", "queue-key-1")).thenReturn(false);

            // when & then
            assertThatThrownBy(() -> reservationService.createReservation(userId, request))
                    .isInstanceOf(CustomException.class)
                    .satisfies(ex -> assertThat(((CustomException) ex).getErrorCode())
                            .isEqualTo(ReservationErrorCode.NOT_ADMITTED));
        }

        @Test
        @DisplayName("이벤트가 OPEN이 아니면 EVENT_NOT_OPEN 예외가 발생한다")
        void create_eventNotOpen_throwsException() {
            // given
            Long userId = 1L;
            Event event = Event.builder()
                    .name("테스트").description("설명")
                    .startsAt(LocalDateTime.now()).endsAt(LocalDateTime.now().plusDays(1))
                    .build(); // DRAFT 상태
            setField(event, "id", 1L);
            ReservationCreateRequest request = new ReservationCreateRequest(1L, 10L, 2, "queue-key-1");

            when(queueRedisRepository.isAdmitted("1", "queue-key-1")).thenReturn(true);
            when(eventRepository.findById(1L)).thenReturn(Optional.of(event));

            // when & then
            assertThatThrownBy(() -> reservationService.createReservation(userId, request))
                    .isInstanceOf(CustomException.class)
                    .satisfies(ex -> assertThat(((CustomException) ex).getErrorCode())
                            .isEqualTo(ReservationErrorCode.EVENT_NOT_OPEN));
        }

        @Test
        @DisplayName("좌석이 해당 이벤트에 속하지 않으면 SEAT_NOT_BELONG_TO_EVENT 예외가 발생한다")
        void create_seatNotBelongToEvent_throwsException() {
            // given
            Long userId = 1L;
            Event event = createEvent(1L);
            Event otherEvent = createEvent(99L);
            Seat seat = createSeat(10L, otherEvent); // 다른 이벤트의 좌석
            ReservationCreateRequest request = new ReservationCreateRequest(1L, 10L, 2, "queue-key-1");

            when(queueRedisRepository.isAdmitted("1", "queue-key-1")).thenReturn(true);
            when(eventRepository.findById(1L)).thenReturn(Optional.of(event));
            when(seatRepository.findById(10L)).thenReturn(Optional.of(seat));

            // when & then
            assertThatThrownBy(() -> reservationService.createReservation(userId, request))
                    .isInstanceOf(CustomException.class)
                    .satisfies(ex -> assertThat(((CustomException) ex).getErrorCode())
                            .isEqualTo(ReservationErrorCode.SEAT_NOT_BELONG_TO_EVENT));
        }

        @Test
        @DisplayName("Redis 좌석 잠금 실패 시 SEAT_LOCK_FAILED 예외가 발생한다")
        void create_lockFailed_throwsException() {
            // given
            Long userId = 1L;
            Event event = createEvent(1L);
            Seat seat = createSeat(10L, event);
            ReservationCreateRequest request = new ReservationCreateRequest(1L, 10L, 2, "queue-key-1");

            when(queueRedisRepository.isAdmitted("1", "queue-key-1")).thenReturn(true);
            when(eventRepository.findById(1L)).thenReturn(Optional.of(event));
            when(seatRepository.findById(10L)).thenReturn(Optional.of(seat));
            when(seatLockRedisRepository.lock(eq(1L), eq(10L), eq(userId), eq(2), anyInt())).thenReturn(false);

            // when & then
            assertThatThrownBy(() -> reservationService.createReservation(userId, request))
                    .isInstanceOf(CustomException.class)
                    .satisfies(ex -> assertThat(((CustomException) ex).getErrorCode())
                            .isEqualTo(ReservationErrorCode.SEAT_LOCK_FAILED));
        }
    }

    @Nested
    @DisplayName("예약 확정")
    class ConfirmReservation {

        @Test
        @DisplayName("HOLDING 예약을 확정하면 CONFIRMED 상태로 변경된다")
        void confirm_success() {
            // given
            Event event = createEvent(1L);
            Seat seat = createSeat(10L, event);
            Reservation reservation = createReservation(1L, event, seat, 1L);

            when(reservationRepository.findByReservationCodeForUpdate(reservation.getReservationCode()))
                    .thenReturn(Optional.of(reservation));
            when(seatRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(seat));

            Claims claims = mock(Claims.class);
            when(claims.get("evt", String.class)).thenReturn("1");
            when(claims.get("res", String.class)).thenReturn(reservation.getReservationCode());
            when(claims.get("id", Long.class)).thenReturn(1L);
            when(jwtProvider.validateToken(anyString())).thenReturn(true);
            when(jwtProvider.getClaims(anyString())).thenReturn(claims);

            Counter counter = mock(Counter.class);
            when(meterRegistry.counter(eq("reservation.success.total"), eq("eventId"), eq("1")))
                    .thenReturn(counter);

            // when
            ReservationResponse response = reservationService.confirmReservation(1L, reservation.getReservationCode(),
                    "valid-token");

            // then
            assertThat(response.status()).isEqualTo(ReservationStatus.CONFIRMED);
            verify(seatRepository).findByIdForUpdate(10L);
        }

        @Test
        @DisplayName("다른 사용자의 예약 확정 시 UNAUTHORIZED 예외가 발생한다")
        void confirm_unauthorized_throwsException() {
            // given
            Event event = createEvent(1L);
            Seat seat = createSeat(10L, event);
            Reservation reservation = createReservation(1L, event, seat, 1L);

            when(reservationRepository.findByReservationCodeForUpdate(reservation.getReservationCode()))
                    .thenReturn(Optional.of(reservation));
            when(jwtProvider.validateToken(anyString())).thenReturn(true);
            Claims claims = mock(Claims.class);
            when(claims.get("evt", String.class)).thenReturn("1");
            when(claims.get("res", String.class)).thenReturn(reservation.getReservationCode());
            when(claims.get("id", Long.class)).thenReturn(999L);
            when(jwtProvider.getClaims(anyString())).thenReturn(claims);

            // when & then
            assertThatThrownBy(
                    () -> reservationService.confirmReservation(999L, reservation.getReservationCode(), "valid-token"))
                    .isInstanceOf(CustomException.class)
                    .satisfies(ex -> assertThat(((CustomException) ex).getErrorCode())
                            .isEqualTo(ReservationErrorCode.UNAUTHORIZED));
        }

        @Test
        @DisplayName("선점 시간이 만료된 예약 확정 시 RESERVATION_EXPIRED 예외가 발생한다")
        void confirm_expired_throwsException() {
            // given
            Event event = createEvent(1L);
            Seat seat = createSeat(10L, event);
            Reservation reservation = createReservation(1L, event, seat, 1L);
            // createdAt을 6분 전으로 설정 (TTL 300초 초과)
            setField(reservation, "createdAt", LocalDateTime.now().minusSeconds(360));

            when(reservationRepository.findByReservationCodeForUpdate(reservation.getReservationCode()))
                    .thenReturn(Optional.of(reservation));
            when(jwtProvider.validateToken(anyString())).thenReturn(true);
            Claims claims = mock(Claims.class);
            when(claims.get("evt", String.class)).thenReturn("1");
            when(claims.get("res", String.class)).thenReturn(reservation.getReservationCode());
            when(claims.get("id", Long.class)).thenReturn(1L);
            when(jwtProvider.getClaims(anyString())).thenReturn(claims);

            // when & then
            assertThatThrownBy(
                    () -> reservationService.confirmReservation(1L, reservation.getReservationCode(), "valid-token"))
                    .isInstanceOf(CustomException.class)
                    .satisfies(ex -> assertThat(((CustomException) ex).getErrorCode())
                            .isEqualTo(ReservationErrorCode.RESERVATION_EXPIRED));
        }
    }

    @Nested
    @DisplayName("예약 취소")
    class CancelReservation {

        @Test
        @DisplayName("HOLDING 예약 취소 시 Redis 잠금이 해제되고 대기열 admitted가 정리된다")
        void cancel_holding_restoresRedis() {
            // given
            Event event = createEvent(1L);
            Seat seat = createSeat(10L, event);
            Reservation reservation = createReservation(1L, event, seat, 1L);

            when(reservationRepository.findByReservationCodeForUpdate(reservation.getReservationCode()))
                    .thenReturn(Optional.of(reservation));

            // when
            ReservationResponse response = reservationService.cancelReservation(1L, reservation.getReservationCode());

            // then
            assertThat(response.status()).isEqualTo(ReservationStatus.CANCELLED);
            verify(seatLockRedisRepository).unlockAndRestore(1L, 10L, 1L);
            verify(queueRedisRepository).removeAdmittedUser("1", "1");
        }

        @Test
        @DisplayName("CONFIRMED 예약 취소 시 좌석 복구 + 대기열 admitted 정리가 된다")
        void cancel_confirmed_restoresSeatAndCleansQueue() {
            // given
            Event event = createEvent(1L);
            Seat seat = createSeat(10L, event);
            Reservation reservation = createReservation(1L, event, seat, 1L);
            reservation.confirm(); // HOLDING → CONFIRMED

            when(reservationRepository.findByReservationCodeForUpdate(reservation.getReservationCode()))
                    .thenReturn(Optional.of(reservation));
            when(seatRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(seat));

            // when
            ReservationResponse response = reservationService.cancelReservation(1L, reservation.getReservationCode());

            // then
            assertThat(response.status()).isEqualTo(ReservationStatus.CANCELLED);
            verify(seatRepository).findByIdForUpdate(10L);
            verify(seatLockRedisRepository).initAvailable(eq(1L), eq(10L), anyInt());
            verify(queueRedisRepository).removeAdmittedUser("1", "1");
        }

        @Test
        @DisplayName("이미 취소된 예약을 다시 취소하면 ALREADY_CANCELLED 예외가 발생한다")
        void cancel_alreadyCancelled_throwsException() {
            // given
            Event event = createEvent(1L);
            Seat seat = createSeat(10L, event);
            Reservation reservation = createReservation(1L, event, seat, 1L);
            reservation.cancel();

            when(reservationRepository.findByReservationCodeForUpdate(reservation.getReservationCode()))
                    .thenReturn(Optional.of(reservation));

            // when & then
            assertThatThrownBy(() -> reservationService.cancelReservation(1L, reservation.getReservationCode()))
                    .isInstanceOf(CustomException.class)
                    .satisfies(ex -> assertThat(((CustomException) ex).getErrorCode())
                            .isEqualTo(ReservationErrorCode.ALREADY_CANCELLED));
        }
    }
}
