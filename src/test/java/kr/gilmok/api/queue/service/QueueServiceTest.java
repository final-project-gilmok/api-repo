package kr.gilmok.api.queue.service;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import kr.gilmok.api.policy.dto.PolicyCacheDto;
import kr.gilmok.api.queue.QueueStatus;
import kr.gilmok.api.queue.dto.QueueRegisterRequest;
import kr.gilmok.api.queue.dto.QueueRegisterResponse;
import kr.gilmok.api.queue.dto.QueueStatusResponse;
import kr.gilmok.api.queue.repository.QueueRedisRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class QueueServiceTest {

    @Mock
    private QueueRedisRepository queueRedisRepository;

    private final MeterRegistry meterRegistry = new SimpleMeterRegistry();

    private QueueService queueService;

    private static final Long USER_ID = 1L;
    private static final String USER_ID_STR = "1";
    private static final PolicyCacheDto TEST_POLICY = new PolicyCacheDto(
            true, 1L, 10, 0, 1L, null, 0, 10, "ROUTING_ENABLED");

    @BeforeEach
    void setUp() {
        queueService = new QueueService(queueRedisRepository, meterRegistry);
        ReflectionTestUtils.setField(queueService, "admissionRps", 10);
        ReflectionTestUtils.setField(queueService, "admittedTtlSeconds", 300);
        ReflectionTestUtils.setField(queueService, "gracePeriodSeconds", 180);
        ReflectionTestUtils.setField(queueService, "defaultEventId", "default");
    }

    private QueueRegisterRequest createRequest(String eventId) {
        try {
            QueueRegisterRequest request = new QueueRegisterRequest();
            var eventIdField = QueueRegisterRequest.class.getDeclaredField("eventId");
            eventIdField.setAccessible(true);
            eventIdField.set(request, eventId);
            return request;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // === register 테스트 ===

    @Test
    @DisplayName("등록 - 신규 등록 시 새 queueKey와 position을 반환한다")
    void register_newUser_returnsQueueKeyAndPosition() {
        // given
        QueueRegisterRequest request = createRequest("event1");
        given(queueRedisRepository.registerIdempotent(
                eq("event1"), eq(USER_ID_STR), anyString(), anyDouble(), anyInt()))
                .willReturn(Arrays.asList(1L, "new-queue-key", 0L, 100L, 50L));

        // when
        QueueRegisterResponse response = queueService.register(USER_ID, request, TEST_POLICY);

        // then
        assertThat(response.getQueueKey()).isEqualTo("new-queue-key");
        assertThat(response.getPosition()).isEqualTo(1);
        verify(queueRedisRepository).registerIdempotent(
                eq("event1"), eq(USER_ID_STR), anyString(), anyDouble(), anyInt());
        // metrics come from Lua return values — no extra Redis calls
        verify(queueRedisRepository, never()).getQueueSize(anyString());
        verify(queueRedisRepository, never()).getAdmittedCount(anyString());
    }

    @Test
    @DisplayName("등록 - 중복 등록 시 기존 queueKey를 반환한다 (멱등)")
    void register_duplicateUser_returnsExistingQueueKey() {
        // given
        QueueRegisterRequest request = createRequest("event1");
        given(queueRedisRepository.registerIdempotent(
                eq("event1"), eq(USER_ID_STR), anyString(), anyDouble(), anyInt()))
                .willReturn(Arrays.asList(0L, "existing-queue-key", 5L, 100L, 50L));

        // when
        QueueRegisterResponse response = queueService.register(USER_ID, request, TEST_POLICY);

        // then
        assertThat(response.getQueueKey()).isEqualTo("existing-queue-key");
        assertThat(response.getPosition()).isEqualTo(6);
        // isNew=0이므로 메트릭 갱신 안 함
        verify(queueRedisRepository, never()).getQueueSize(anyString());
    }

    @Test
    @DisplayName("등록 - 이미 admitted 상태면 기존 queueKey를 position=0으로 반환한다")
    void register_alreadyAdmitted_returnsExistingQueueKey() {
        // given
        QueueRegisterRequest request = createRequest("event1");
        given(queueRedisRepository.registerIdempotent(
                eq("event1"), eq(USER_ID_STR), anyString(), anyDouble(), anyInt()))
                .willReturn(Arrays.asList(-1L, "admitted-queue-key", -1L, 100L, 50L));

        // when
        QueueRegisterResponse response = queueService.register(USER_ID, request, TEST_POLICY);

        // then
        assertThat(response.getQueueKey()).isEqualTo("admitted-queue-key");
        assertThat(response.getPosition()).isEqualTo(0);
        assertThat(response.getEtaSeconds()).isEqualTo(0);
    }

    @Test
    @DisplayName("등록 - policy가 null이면 기본 admissionRps로 ETA를 계산한다")
    void register_nullPolicy_fallsBackToDefaultRps() {
        // given — admissionRps=10, rank=9 → position=10, ETA=10/10=1
        QueueRegisterRequest request = createRequest("event1");
        given(queueRedisRepository.registerIdempotent(
                eq("event1"), eq(USER_ID_STR), anyString(), anyDouble(), anyInt()))
                .willReturn(Arrays.asList(1L, "new-queue-key", 9L, 100L, 50L));

        // when — policy=null (PolicyFilter를 거치지 않은 경우)
        QueueRegisterResponse response = queueService.register(USER_ID, request, null);

        // then
        assertThat(response.getEtaSeconds()).isEqualTo(1); // 10 / default(10)
    }

    @Test
    @DisplayName("등록 - policy의 admissionRps가 기본값과 다르면 해당 값으로 ETA를 계산한다")
    void register_policyOverridesAdmissionRps() {
        // given — policy admissionRps=50, rank=49 → position=50, ETA=50/50=1
        PolicyCacheDto customPolicy = new PolicyCacheDto(
                true, 1L, 50, 0, 1L, null, 0, 10, "ROUTING_ENABLED");
        QueueRegisterRequest request = createRequest("event1");
        given(queueRedisRepository.registerIdempotent(
                eq("event1"), eq(USER_ID_STR), anyString(), anyDouble(), anyInt()))
                .willReturn(Arrays.asList(1L, "new-queue-key", 49L, 200L, 100L));

        // when
        QueueRegisterResponse response = queueService.register(USER_ID, request, customPolicy);

        // then — default(10)였으면 ETA=5, policy(50)이면 ETA=1
        assertThat(response.getEtaSeconds()).isEqualTo(1);
    }

    // === getStatus 테스트 ===

    @Test
    @DisplayName("상태 조회 - admitted 상태면 ADMITTABLE을 반환한다")
    void getStatus_admitted_returnsAdmittable() {
        // given — statusCode=1
        given(queueRedisRepository.getQueueOwnerUserId("event1", "queue1"))
                .willReturn(USER_ID);
        given(queueRedisRepository.getStatusAtomic("event1", "queue1", 60))
                .willReturn(Arrays.asList(1L, -1L, 0L, 0L));

        // when
        QueueStatusResponse response = queueService.getStatus("event1", "queue1", "testuser", USER_ID);

        // then
        assertThat(response.getStatus()).isEqualTo(QueueStatus.ADMITTABLE);
        assertThat(response.getPollAfterMs()).isEqualTo(0);
    }

    @Test
    @DisplayName("상태 조회 - 대기 중이면 WAITING과 position, total을 반환한다")
    void getStatus_waiting_returnsWaitingWithPosition() {
        // given — statusCode=2, rank=4, total=100, admitCount=20
        given(queueRedisRepository.getQueueOwnerUserId("event1", "queue1"))
                .willReturn(USER_ID);
        given(queueRedisRepository.getStatusAtomic("event1", "queue1", 60))
                .willReturn(Arrays.asList(2L, 4L, 100L, 20L));

        // when
        QueueStatusResponse response = queueService.getStatus("event1", "queue1", "testuser", USER_ID);

        // then
        assertThat(response.getStatus()).isEqualTo(QueueStatus.WAITING);
        assertThat(response.getPosition()).isEqualTo(5);
        assertThat(response.getTotal()).isEqualTo(100);
    }

    @Test
    @DisplayName("상태 조회 - 만료 상태면 EXPIRED를 반환한다")
    void getStatus_notFound_returnsExpired() {
        // given — statusCode=3
        given(queueRedisRepository.getQueueOwnerUserId("event1", "queue1"))
                .willReturn(USER_ID);
        given(queueRedisRepository.getStatusAtomic("event1", "queue1", 60))
                .willReturn(Arrays.asList(3L, -1L, 0L, 0L));

        // when
        QueueStatusResponse response = queueService.getStatus("event1", "queue1", "testuser", USER_ID);

        // then
        assertThat(response.getStatus()).isEqualTo(QueueStatus.EXPIRED);
        assertThat(response.getPosition()).isEqualTo(0);
        assertThat(response.getPollAfterMs()).isEqualTo(0);
    }

    // === 동적 폴링 간격 테스트 ===

    @Test
    @DisplayName("동적 폴링 - position 1000 이상이면 5000ms를 반환한다")
    void getStatus_position1000Plus_returns5000ms() {
        // given — rank=1499 → position=1500
        given(queueRedisRepository.getQueueOwnerUserId("event1", "queue1"))
                .willReturn(USER_ID);
        given(queueRedisRepository.getStatusAtomic("event1", "queue1", 60))
                .willReturn(Arrays.asList(2L, 1499L, 2000L, 0L));

        // when
        QueueStatusResponse response = queueService.getStatus("event1", "queue1", "testuser", USER_ID);

        // then
        assertThat(response.getPollAfterMs()).isEqualTo(5000);
    }

    @Test
    @DisplayName("동적 폴링 - position 100~999이면 3000ms를 반환한다")
    void getStatus_position100to999_returns3000ms() {
        // given — rank=499 → position=500
        given(queueRedisRepository.getQueueOwnerUserId("event1", "queue1"))
                .willReturn(USER_ID);
        given(queueRedisRepository.getStatusAtomic("event1", "queue1", 60))
                .willReturn(Arrays.asList(2L, 499L, 1000L, 0L));

        // when
        QueueStatusResponse response = queueService.getStatus("event1", "queue1", "testuser", USER_ID);

        // then
        assertThat(response.getPollAfterMs()).isEqualTo(3000);
    }

    @Test
    @DisplayName("동적 폴링 - position 100 미만이면 1000ms를 반환한다")
    void getStatus_positionUnder100_returns1000ms() {
        // given — rank=9 → position=10
        given(queueRedisRepository.getQueueOwnerUserId("event1", "queue1"))
                .willReturn(USER_ID);
        given(queueRedisRepository.getStatusAtomic("event1", "queue1", 60))
                .willReturn(Arrays.asList(2L, 9L, 50L, 0L));

        // when
        QueueStatusResponse response = queueService.getStatus("event1", "queue1", "testuser", USER_ID);

        // then
        assertThat(response.getPollAfterMs()).isEqualTo(1000);
    }

    // === ETA 테스트 ===

    @Test
    @DisplayName("ETA - admitCount가 있으면 실측 rps로 계산한다")
    void getStatus_withAdmitCount_usesRealRpsForEta() {
        // given — rank=99 → position=100, admitCount=60 in 60s → rps=1
        given(queueRedisRepository.getQueueOwnerUserId("event1", "queue1"))
                .willReturn(USER_ID);
        given(queueRedisRepository.getStatusAtomic("event1", "queue1", 60))
                .willReturn(Arrays.asList(2L, 99L, 200L, 60L));

        // when
        QueueStatusResponse response = queueService.getStatus("event1", "queue1", "testuser", USER_ID);

        // then — 100 / (60/60) = 100초
        assertThat(response.getEtaSeconds()).isEqualTo(100);
    }

    @Test
    @DisplayName("ETA - admitCount가 0이면 admissionRps로 폴백한다")
    void getStatus_withoutAdmitCount_fallsBackToAdmissionRps() {
        // given — rank=99 → position=100, admitCount=0
        given(queueRedisRepository.getQueueOwnerUserId("event1", "queue1"))
                .willReturn(USER_ID);
        given(queueRedisRepository.getStatusAtomic("event1", "queue1", 60))
                .willReturn(Arrays.asList(2L, 99L, 200L, 0L));

        // when
        QueueStatusResponse response = queueService.getStatus("event1", "queue1", "testuser", USER_ID);

        // then — 100 / 10 = 10초
        assertThat(response.getEtaSeconds()).isEqualTo(10);
    }

    // === runAdmissionCycle 테스트 ===

    @Test
    @DisplayName("입장 사이클 - 입장 성공 시 recordAdmissionRate와 updateSessions가 호출된다")
    void runAdmissionCycle_admitsUsers_recordsAndUpdatesSessions() {
        // given — 결과: expired=2, cleaned=1, admitted=3, consumed=3, left=7, waiting=5,
        // admittedSize=3, members
        given(queueRedisRepository.runAdmissionCycle(
                eq("event1"), anyLong(), anyLong(),
                eq(300_000L), eq(180_000L), eq(100), eq(100), eq(0)))
                .willReturn(Arrays.asList(2L, 1L, 3L, 3L, 7L, 5L, 3L, "m1", "m2", "m3"));

        // when
        queueService.runAdmissionCycle("event1", 10, 0);

        // then
        verify(queueRedisRepository).recordAdmissionRate(eq("event1"), eq(3L), anyLong());
        verify(queueRedisRepository).updateSessionsToAdmitted(eq("event1"), eq(List.of("m1", "m2", "m3")), anyInt());
    }

    @Test
    @DisplayName("입장 사이클 - 변화 없을 때 recordAdmissionRate가 호출되지 않는다")
    void runAdmissionCycle_noChanges_doesNotRecord() {
        // given — 모두 0
        given(queueRedisRepository.runAdmissionCycle(
                eq("event1"), anyLong(), anyLong(),
                eq(300_000L), eq(180_000L), eq(100), eq(100), eq(0)))
                .willReturn(Arrays.asList(0L, 0L, 0L, 0L, 10L, 5L, 0L));

        // when
        queueService.runAdmissionCycle("event1", 10, 0);

        // then
        verify(queueRedisRepository, never()).recordAdmissionRate(anyString(), anyLong(), anyLong());
        verify(queueRedisRepository, never()).updateSessionsToAdmitted(anyString(), anyList(), anyInt());
    }

    @Test
    @DisplayName("입장 사이클 - 반환값으로 메트릭이 갱신된다 (Redis 추가 호출 없음)")
    void runAdmissionCycle_updatesMetricsFromReturnValues() {
        // given
        given(queueRedisRepository.runAdmissionCycle(
                eq("event1"), anyLong(), anyLong(),
                eq(300_000L), eq(180_000L), eq(100), eq(100), eq(0)))
                .willReturn(Arrays.asList(0L, 0L, 0L, 0L, 10L, 42L, 7L));

        // when
        queueService.runAdmissionCycle("event1", 10, 0);

        // then — getQueueSize/getAdmittedCount 호출 없어야 함 (반환값 사용)
        verify(queueRedisRepository, never()).getQueueSize("event1");
        verify(queueRedisRepository, never()).getAdmittedCount("event1");
    }
}
