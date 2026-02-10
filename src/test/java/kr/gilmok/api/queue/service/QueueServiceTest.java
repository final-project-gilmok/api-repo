package kr.gilmok.api.queue.service;

import kr.gilmok.api.queue.QueueStatus;
import kr.gilmok.api.queue.dto.QueueRegisterRequest;
import kr.gilmok.api.queue.dto.QueueRegisterResponse;
import kr.gilmok.api.queue.dto.QueueStatusResponse;
import kr.gilmok.api.queue.repository.QueueRedisRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class QueueServiceTest {

    @Mock
    private QueueRedisRepository queueRedisRepository;

    @InjectMocks
    private QueueService queueService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(queueService, "admissionRps", 10);
        ReflectionTestUtils.setField(queueService, "admittedTtlSeconds", 300);
    }

    private QueueRegisterRequest createRequest(String eventId, String sessionKey) {
        try {
            QueueRegisterRequest request = new QueueRegisterRequest();
            var eventIdField = QueueRegisterRequest.class.getDeclaredField("eventId");
            eventIdField.setAccessible(true);
            eventIdField.set(request, eventId);

            var sessionKeyField = QueueRegisterRequest.class.getDeclaredField("sessionKey");
            sessionKeyField.setAccessible(true);
            sessionKeyField.set(request, sessionKey);

            return request;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // === register 테스트 ===

    @Test
    @DisplayName("신규 등록 - queueKey와 position을 반환한다")
    void register_newUser_returnsQueueKeyAndPosition() {
        // given
        QueueRegisterRequest request = createRequest("event1", "session1");
        given(queueRedisRepository.findQueueKeyBySession("event1", "session1")).willReturn(null);
        given(queueRedisRepository.register(eq("event1"), eq("session1"), anyString(), anyDouble())).willReturn(true);
        given(queueRedisRepository.getRank(eq("event1"), anyString())).willReturn(0L);

        // when
        QueueRegisterResponse response = queueService.register(request);

        // then
        assertThat(response.getQueueKey()).isNotNull();
        assertThat(response.getPosition()).isEqualTo(1);
        verify(queueRedisRepository).register(eq("event1"), eq("session1"), anyString(), anyDouble());
    }

    @Test
    @DisplayName("재입장 - 동일 sessionKey로 요청 시 기존 queueKey를 반환한다")
    void register_existingSession_returnsSameQueueKey() {
        // given
        String existingQueueKey = "existing-uuid";
        QueueRegisterRequest request = createRequest("event1", "session1");
        given(queueRedisRepository.findQueueKeyBySession("event1", "session1")).willReturn(existingQueueKey);
        given(queueRedisRepository.getRank("event1", existingQueueKey)).willReturn(2L);

        // when
        QueueRegisterResponse response = queueService.register(request);

        // then
        assertThat(response.getQueueKey()).isEqualTo(existingQueueKey);
        assertThat(response.getPosition()).isEqualTo(3);
        verify(queueRedisRepository, never()).register(anyString(), anyString(), anyString(), anyDouble());
    }

    @Test
    @DisplayName("재입장 - 이미 admitted 상태면 position 0을 반환한다")
    void register_alreadyAdmitted_returnsPositionZero() {
        // given
        String existingQueueKey = "admitted-uuid";
        QueueRegisterRequest request = createRequest("event1", "session1");
        given(queueRedisRepository.findQueueKeyBySession("event1", "session1")).willReturn(existingQueueKey);
        given(queueRedisRepository.getRank("event1", existingQueueKey)).willReturn(null);
        given(queueRedisRepository.isAdmitted("event1", existingQueueKey)).willReturn(true);

        // when
        QueueRegisterResponse response = queueService.register(request);

        // then
        assertThat(response.getQueueKey()).isEqualTo(existingQueueKey);
        assertThat(response.getPosition()).isEqualTo(0);
        assertThat(response.getEtaSeconds()).isEqualTo(0);
    }

    // === getStatus 테스트 ===

    @Test
    @DisplayName("상태 조회 - admitted 상태면 ADMITTABLE을 반환한다")
    void getStatus_admitted_returnsAdmittable() {
        // given
        given(queueRedisRepository.isAdmitted("event1", "queue1")).willReturn(true);

        // when
        QueueStatusResponse response = queueService.getStatus("event1", "queue1");

        // then
        assertThat(response.getStatus()).isEqualTo(QueueStatus.ADMITTABLE);
        assertThat(response.getPosition()).isEqualTo(0);
        assertThat(response.getPollAfterMs()).isEqualTo(0);
    }

    @Test
    @DisplayName("상태 조회 - 대기 중이면 WAITING과 position을 반환한다")
    void getStatus_waiting_returnsWaitingWithPosition() {
        // given
        given(queueRedisRepository.isAdmitted("event1", "queue1")).willReturn(false);
        given(queueRedisRepository.getRank("event1", "queue1")).willReturn(4L);

        // when
        QueueStatusResponse response = queueService.getStatus("event1", "queue1");

        // then
        assertThat(response.getStatus()).isEqualTo(QueueStatus.WAITING);
        assertThat(response.getPosition()).isEqualTo(5);
        assertThat(response.getPollAfterMs()).isEqualTo(3000);
    }

    @Test
    @DisplayName("상태 조회 - 대기열에도 admitted에도 없으면 EXPIRED를 반환한다")
    void getStatus_notFound_returnsExpired() {
        // given
        given(queueRedisRepository.isAdmitted("event1", "queue1")).willReturn(false);
        given(queueRedisRepository.getRank("event1", "queue1")).willReturn(null);

        // when
        QueueStatusResponse response = queueService.getStatus("event1", "queue1");

        // then
        assertThat(response.getStatus()).isEqualTo(QueueStatus.EXPIRED);
        assertThat(response.getPosition()).isEqualTo(0);
        assertThat(response.getPollAfterMs()).isEqualTo(0);
    }

    // === processAdmission 테스트 ===

    @Test
    @DisplayName("입장 처리 - 대기열이 비어있으면 아무 작업도 하지 않는다")
    void processAdmission_emptyQueue_doesNothing() {
        // given
        given(queueRedisRepository.getQueueSize("event1")).willReturn(0L);

        // when
        queueService.processAdmission("event1");

        // then
        verify(queueRedisRepository, never()).consumeTokens(anyString(), anyLong(), anyLong());
        verify(queueRedisRepository, never()).admitFromHead(anyString(), anyLong());
    }

    @Test
    @DisplayName("입장 처리 - 토큰이 있으면 대기열에서 꺼내 admitted로 이동한다")
    void processAdmission_tokensAvailable_admitsUsers() {
        // given
        given(queueRedisRepository.getQueueSize("event1")).willReturn(5L);
        given(queueRedisRepository.consumeTokens(eq("event1"), anyLong(), eq(5L))).willReturn(3L);

        // when
        queueService.processAdmission("event1");

        // then
        verify(queueRedisRepository).admitFromHead("event1", 3);
    }

    @Test
    @DisplayName("입장 처리 - 토큰이 0이면 입장시키지 않는다")
    void processAdmission_noTokens_doesNotAdmit() {
        // given
        given(queueRedisRepository.getQueueSize("event1")).willReturn(5L);
        given(queueRedisRepository.consumeTokens(eq("event1"), anyLong(), eq(5L))).willReturn(0L);

        // when
        queueService.processAdmission("event1");

        // then
        verify(queueRedisRepository, never()).admitFromHead(anyString(), anyLong());
    }

    // === expireAdmitted 테스트 ===

    @Test
    @DisplayName("만료 처리 - TTL이 지난 admitted 항목을 삭제한다")
    void expireAdmitted_removesExpiredEntries() {
        // given
        given(queueRedisRepository.removeExpiredAdmitted(eq("event1"), anyLong())).willReturn(3L);

        // when
        queueService.expireAdmitted("event1");

        // then
        verify(queueRedisRepository).removeExpiredAdmitted(eq("event1"), anyLong());
    }

    @Test
    @DisplayName("만료 처리 - 삭제할 항목이 없으면 로그만 남긴다")
    void expireAdmitted_noExpiredEntries_logsOnly() {
        // given
        given(queueRedisRepository.removeExpiredAdmitted(eq("event1"), anyLong())).willReturn(0L);

        // when
        queueService.expireAdmitted("event1");

        // then
        verify(queueRedisRepository).removeExpiredAdmitted(eq("event1"), anyLong());
    }
}
