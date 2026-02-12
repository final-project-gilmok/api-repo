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
        ReflectionTestUtils.setField(queueService, "gracePeriodSeconds", 180);
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
        given(queueRedisRepository.getMovingAverageRps(eq("event1"), anyLong())).willReturn(null);

        // when
        QueueRegisterResponse response = queueService.register(request);

        // then
        assertThat(response.getQueueKey()).isNotNull();
        assertThat(response.getPosition()).isEqualTo(1);
        verify(queueRedisRepository).register(eq("event1"), eq("session1"), anyString(), anyDouble());
        verify(queueRedisRepository).updateHeartbeat(eq("event1"), anyString());
    }

    @Test
    @DisplayName("재입장 - 동일 sessionKey로 요청 시 기존 queueKey를 반환한다")
    void register_existingSession_returnsSameQueueKey() {
        // given
        String existingQueueKey = "existing-uuid";
        QueueRegisterRequest request = createRequest("event1", "session1");
        given(queueRedisRepository.findQueueKeyBySession("event1", "session1")).willReturn(existingQueueKey);
        given(queueRedisRepository.getRank("event1", existingQueueKey)).willReturn(2L);
        given(queueRedisRepository.getMovingAverageRps(eq("event1"), anyLong())).willReturn(null);

        // when
        QueueRegisterResponse response = queueService.register(request);

        // then
        assertThat(response.getQueueKey()).isEqualTo(existingQueueKey);
        assertThat(response.getPosition()).isEqualTo(3);
        verify(queueRedisRepository, never()).register(anyString(), anyString(), anyString(), anyDouble());
        verify(queueRedisRepository).updateHeartbeat("event1", existingQueueKey);
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
        verify(queueRedisRepository).updateHeartbeat("event1", "queue1");
    }

    @Test
    @DisplayName("상태 조회 - 대기 중이면 WAITING과 position, total을 반환한다")
    void getStatus_waiting_returnsWaitingWithPosition() {
        // given
        given(queueRedisRepository.isAdmitted("event1", "queue1")).willReturn(false);
        given(queueRedisRepository.getRank("event1", "queue1")).willReturn(4L);
        given(queueRedisRepository.getQueueSize("event1")).willReturn(100L);
        given(queueRedisRepository.getMovingAverageRps(eq("event1"), anyLong())).willReturn(null);

        // when
        QueueStatusResponse response = queueService.getStatus("event1", "queue1");

        // then
        assertThat(response.getStatus()).isEqualTo(QueueStatus.WAITING);
        assertThat(response.getPosition()).isEqualTo(5);
        assertThat(response.getTotal()).isEqualTo(100);
        verify(queueRedisRepository).updateHeartbeat("event1", "queue1");
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

    // === 동적 폴링 간격 테스트 ===

    @Test
    @DisplayName("동적 폴링 - position 1000 이상이면 5000ms를 반환한다")
    void getStatus_position1000Plus_returns5000ms() {
        // given
        given(queueRedisRepository.isAdmitted("event1", "queue1")).willReturn(false);
        given(queueRedisRepository.getRank("event1", "queue1")).willReturn(1499L); // position = 1500
        given(queueRedisRepository.getQueueSize("event1")).willReturn(2000L);
        given(queueRedisRepository.getMovingAverageRps(eq("event1"), anyLong())).willReturn(null);

        // when
        QueueStatusResponse response = queueService.getStatus("event1", "queue1");

        // then
        assertThat(response.getPollAfterMs()).isEqualTo(5000);
    }

    @Test
    @DisplayName("동적 폴링 - position 100~999이면 3000ms를 반환한다")
    void getStatus_position100to999_returns3000ms() {
        // given
        given(queueRedisRepository.isAdmitted("event1", "queue1")).willReturn(false);
        given(queueRedisRepository.getRank("event1", "queue1")).willReturn(499L); // position = 500
        given(queueRedisRepository.getQueueSize("event1")).willReturn(1000L);
        given(queueRedisRepository.getMovingAverageRps(eq("event1"), anyLong())).willReturn(null);

        // when
        QueueStatusResponse response = queueService.getStatus("event1", "queue1");

        // then
        assertThat(response.getPollAfterMs()).isEqualTo(3000);
    }

    @Test
    @DisplayName("동적 폴링 - position 100 미만이면 1000ms를 반환한다")
    void getStatus_positionUnder100_returns1000ms() {
        // given
        given(queueRedisRepository.isAdmitted("event1", "queue1")).willReturn(false);
        given(queueRedisRepository.getRank("event1", "queue1")).willReturn(9L); // position = 10
        given(queueRedisRepository.getQueueSize("event1")).willReturn(50L);
        given(queueRedisRepository.getMovingAverageRps(eq("event1"), anyLong())).willReturn(null);

        // when
        QueueStatusResponse response = queueService.getStatus("event1", "queue1");

        // then
        assertThat(response.getPollAfterMs()).isEqualTo(1000);
    }

    // === 이동평균 ETA 테스트 ===

    @Test
    @DisplayName("이동평균 ETA - avgRps가 있으면 해당 값으로 ETA를 계산한다")
    void getStatus_withMovingAvgRps_usesAvgForEta() {
        // given
        given(queueRedisRepository.isAdmitted("event1", "queue1")).willReturn(false);
        given(queueRedisRepository.getRank("event1", "queue1")).willReturn(99L); // position = 100
        given(queueRedisRepository.getQueueSize("event1")).willReturn(200L);
        given(queueRedisRepository.getMovingAverageRps("event1", 60_000L)).willReturn(20.0);

        // when
        QueueStatusResponse response = queueService.getStatus("event1", "queue1");

        // then
        // position=100, avgRps=20 → eta = 100/20 = 5
        assertThat(response.getEtaSeconds()).isEqualTo(5);
    }

    @Test
    @DisplayName("이동평균 ETA - avgRps가 null이면 admissionRps로 폴백한다")
    void getStatus_withoutMovingAvgRps_fallsBackToAdmissionRps() {
        // given
        given(queueRedisRepository.isAdmitted("event1", "queue1")).willReturn(false);
        given(queueRedisRepository.getRank("event1", "queue1")).willReturn(99L); // position = 100
        given(queueRedisRepository.getQueueSize("event1")).willReturn(200L);
        given(queueRedisRepository.getMovingAverageRps("event1", 60_000L)).willReturn(null);

        // when
        QueueStatusResponse response = queueService.getStatus("event1", "queue1");

        // then
        // position=100, admissionRps=10 → eta = 100/10 = 10
        assertThat(response.getEtaSeconds()).isEqualTo(10);
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
    @DisplayName("입장 처리 - 토큰이 있으면 대기열에서 꺼내 admitted로 이동하고 admission을 기록한다")
    void processAdmission_tokensAvailable_admitsUsersAndRecords() {
        // given
        given(queueRedisRepository.getQueueSize("event1")).willReturn(5L);
        given(queueRedisRepository.consumeTokens(eq("event1"), anyLong(), eq(5L))).willReturn(3L);
        given(queueRedisRepository.admitFromHead("event1", 3)).willReturn(3L);

        // when
        queueService.processAdmission("event1");

        // then
        verify(queueRedisRepository).admitFromHead("event1", 3);
        verify(queueRedisRepository).recordAdmission("event1", 3);
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

    // === Grace Period 테스트 ===

    @Test
    @DisplayName("Grace Period - cleanupGracePeriod이 호출된다")
    void cleanupGracePeriod_callsRepository() {
        // given
        given(queueRedisRepository.cleanupGracePeriod(eq("event1"), eq(180_000L))).willReturn(2L);

        // when
        queueService.cleanupGracePeriod("event1");

        // then
        verify(queueRedisRepository).cleanupGracePeriod("event1", 180_000L);
    }
}
