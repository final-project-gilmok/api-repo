package kr.gilmok.api.event.service;

import kr.gilmok.api.event.dto.EventCreateRequest;
import kr.gilmok.api.event.dto.EventResponse;
import kr.gilmok.api.event.entity.Event;
import kr.gilmok.api.event.entity.EventStatus;
import kr.gilmok.api.event.repository.EventRepository;
import kr.gilmok.api.event.exception.EventErrorCode;
import kr.gilmok.api.policy.service.PolicyService;
import kr.gilmok.common.exception.CustomException;
import org.springframework.http.HttpStatus;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("EventService 단위 테스트")
class EventServiceTest {

    @Mock
    private EventRepository eventRepository;
    @Mock
    private PolicyService policyService;

    @InjectMocks
    private EventService eventService;

    private static void setEventId(Event event, Long id) {
        try {
            Field idField = Event.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(event, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Nested
    @DisplayName("이벤트 생성")
    class CreateEvent {

        @Test
        @DisplayName("EventCreateRequest를 저장하면 응답의 상태값이 DRAFT이다")
        void createEvent_returnsDraftStatus() {
            // given
            EventCreateRequest request = new EventCreateRequest(
                    "테스트 이벤트",
                    "설명",
                    LocalDateTime.now(),
                    LocalDateTime.now().plusDays(7),
                    null
            );
            when(eventRepository.save(any(Event.class))).thenAnswer(invocation -> {
                Event e = invocation.getArgument(0);
                setEventId(e, 1L);
                return e;
            });

            // when
            EventResponse response = eventService.createEvent(request);

            // then
            assertThat(response).isNotNull();
            assertThat(response.status()).isEqualTo(EventStatus.DRAFT);

            verify(eventRepository).save(any(Event.class));
            verify(policyService).createPolicyForEvent(1L, null);
        }
    }

    @Nested
    @DisplayName("이벤트 오픈")
    class OpenEvent {

        @Test
        @DisplayName("openEvent() 호출 시 엔티티 상태가 OPEN으로 변경된다")
        void openEvent_changesStatusToOpen() {
            // given
            Long eventId = 1L;
            Event event = Event.builder()
                    .name("이벤트")
                    .description("설명")
                    .startsAt(LocalDateTime.now())
                    .endsAt(LocalDateTime.now().plusDays(1))
                    .build();
            setEventId(event, eventId);
            when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));

            // when
            EventResponse response = eventService.openEvent(eventId);

            // then
            assertThat(response).isNotNull();
            assertThat(response.eventId()).isEqualTo(eventId);
            assertThat(response.status()).isEqualTo(EventStatus.OPEN);

            verify(eventRepository).findById(eventId);
        }
    }

    @Nested
    @DisplayName("이벤트 종료")
    class CloseEvent {

        @Test
        @DisplayName("closeEvent() 호출 시 엔티티 상태가 CLOSED로 변경된다")
        void closeEvent_changesStatusToClosed() {
            // given
            Long eventId = 1L;
            Event event = Event.builder()
                    .name("이벤트")
                    .description("설명")
                    .startsAt(LocalDateTime.now())
                    .endsAt(LocalDateTime.now().plusDays(1))
                    .build();
            setEventId(event, eventId);
            when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));

            // when
            EventResponse response = eventService.closeEvent(eventId);

            // then
            assertThat(response).isNotNull();
            assertThat(response.eventId()).isEqualTo(eventId);
            assertThat(response.status()).isEqualTo(EventStatus.CLOSED);

            verify(eventRepository).findById(eventId);
        }
    }

    @Nested
    @DisplayName("이벤트 목록 조회")
    class GetEvents {

        @Test
        @DisplayName("getEvents() 호출 시 생성일 기준 내림차순 목록을 반환한다")
        void getEvents_returnsOrderedList() {
            // given
            LocalDateTime base = LocalDateTime.now();
            Event event1 = Event.builder()
                    .name("첫 번째")
                    .description("설명1")
                    .startsAt(base)
                    .endsAt(base.plusDays(1))
                    .build();
            Event event2 = Event.builder()
                    .name("두 번째")
                    .description("설명2")
                    .startsAt(base)
                    .endsAt(base.plusDays(1))
                    .build();
            setEventId(event1, 1L);
            setEventId(event2, 2L);
            when(eventRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(event1, event2));

            // when
            List<EventResponse> response = eventService.getEvents();

            // then
            assertThat(response).hasSize(2);
            assertThat(response.get(0).eventId()).isEqualTo(1L);
            assertThat(response.get(0).name()).isEqualTo("첫 번째");
            assertThat(response.get(1).eventId()).isEqualTo(2L);
            assertThat(response.get(1).name()).isEqualTo("두 번째");
            verify(eventRepository).findAllByOrderByCreatedAtDesc();
        }

        @Test
        @DisplayName("이벤트가 없으면 빈 목록을 반환한다")
        void getEvents_emptyList() {
            when(eventRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of());

            List<EventResponse> response = eventService.getEvents();

            assertThat(response).isEmpty();
            verify(eventRepository).findAllByOrderByCreatedAtDesc();
        }
    }

    @Nested
    @DisplayName("예외 처리")
    class ExceptionHandling {

        @Test
        @DisplayName("존재하지 않는 eventId로 조회 시 CustomException(E001)이 발생한다")
        void getEvent_withInvalidId_throwsException() {
            // given
            Long invalidEventId = 999L;
            when(eventRepository.findById(invalidEventId)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> eventService.getEvent(invalidEventId))
                    .isInstanceOf(CustomException.class)
                    .satisfies(ex -> {
                        CustomException ce = (CustomException) ex;
                        assertThat(ce.getErrorCode()).isEqualTo(EventErrorCode.EVENT_NOT_FOUND);
                        assertThat(ce.getErrorCode().getCode()).isEqualTo("E001");
                        assertThat(ce.getErrorCode().getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                        assertThat(ce.getErrorCode().getMessage()).isEqualTo("해당 이벤트를 찾을 수 없습니다.");
                    });

            verify(eventRepository).findById(invalidEventId);
        }

        @Test
        @DisplayName("존재하지 않는 eventId로 openEvent 호출 시 CustomException이 발생한다")
        void openEvent_withInvalidId_throwsException() {
            Long invalidEventId = 999L;
            when(eventRepository.findById(invalidEventId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> eventService.openEvent(invalidEventId))
                    .isInstanceOf(CustomException.class)
                    .satisfies(ex -> assertThat(((CustomException) ex).getErrorCode()).isEqualTo(EventErrorCode.EVENT_NOT_FOUND));
        }

        @Test
        @DisplayName("존재하지 않는 eventId로 closeEvent 호출 시 CustomException이 발생한다")
        void closeEvent_withInvalidId_throwsException() {
            Long invalidEventId = 999L;
            when(eventRepository.findById(invalidEventId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> eventService.closeEvent(invalidEventId))
                    .isInstanceOf(CustomException.class)
                    .satisfies(ex -> assertThat(((CustomException) ex).getErrorCode()).isEqualTo(EventErrorCode.EVENT_NOT_FOUND));
        }
    }
}
