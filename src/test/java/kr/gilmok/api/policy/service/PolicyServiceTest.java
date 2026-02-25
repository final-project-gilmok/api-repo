package kr.gilmok.api.policy.service;

import kr.gilmok.api.event.exception.EventErrorCode;
import kr.gilmok.api.event.repository.EventRepository;
import kr.gilmok.api.policy.dto.PolicyCacheDto;
import kr.gilmok.api.policy.dto.PolicyResponse;
import kr.gilmok.api.policy.dto.PolicyUpdateRequest;
import kr.gilmok.api.policy.entity.Policy;
import kr.gilmok.api.policy.exception.PolicyErrorCode;
import kr.gilmok.api.policy.repository.PolicyCacheRepository;
import kr.gilmok.api.policy.repository.PolicyHistoryRepository;
import kr.gilmok.api.policy.repository.PolicyRepository;
import kr.gilmok.common.exception.CustomException;
import kr.gilmok.api.policy.vo.BlockRules;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import jakarta.persistence.OptimisticLockException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PolicyService 단위 테스트")
class PolicyServiceTest {

    @Mock
    private PolicyRepository policyRepository;
    @Mock
    private PolicyHistoryRepository historyRepository;
    @Mock
    private PolicyCacheRepository policyCacheRepository;
    @Mock
    private EventRepository eventRepository;
    @Mock
    private RabbitTemplate rabbitTemplate;

    @InjectMocks
    private PolicyService policyService;

    @Nested
    @DisplayName("정책 조회")
    class GetPolicy {

        @Test
        @DisplayName("캐시에 있으면 Redis에서 반환하고 DB는 조회하지 않는다")
        void getPolicyByEventId_cacheHit_returnsFromRedis() {
            Long eventId = 1L;
            PolicyCacheDto cached = new PolicyCacheDto(true, eventId, 10, 5, 300L, 2L, BlockRules.empty(), 20, 10);
            when(policyCacheRepository.find(eventId)).thenReturn(Optional.of(cached));

            PolicyResponse response = policyService.getPolicyByEventId(eventId);

            assertThat(response).isNotNull();
            assertThat(response.eventId()).isEqualTo(eventId);
            assertThat(response.policyVersion()).isEqualTo(2L);
            assertThat(response.admissionRps()).isEqualTo(10);
            verify(policyCacheRepository).find(eventId);
            verify(policyRepository, never()).findByEventId(any());
            verify(eventRepository, never()).existsById(any());

        }

        @Test
        @DisplayName("캐시에 없으면 DB 조회 후 Redis에 저장하고 PolicyResponse를 반환한다")
        void getPolicyByEventId_cacheMiss_loadsFromDbAndCaches() {
            Long eventId = 1L;
            Policy policy = new Policy(eventId);
            when(policyCacheRepository.find(eventId)).thenReturn(Optional.empty());
            when(policyRepository.findByEventId(eventId)).thenReturn(Optional.of(policy));

            PolicyResponse response = policyService.getPolicyByEventId(eventId);

            assertThat(response).isNotNull();
            assertThat(response.eventId()).isEqualTo(eventId);
            assertThat(response.policyVersion()).isEqualTo(1L);
            verify(policyCacheRepository).find(eventId);
            verify(policyRepository).findByEventId(eventId);
            verify(policyCacheRepository).save(eq(eventId), any(PolicyCacheDto.class));
            verify(eventRepository, never()).existsById(any());

        }

        @Test
        @DisplayName("캐시 미스 후 event가 없으면 EVENT_NOT_FOUND 예외가 발생한다")
        void getPolicyByEventId_eventNotExists_throwsEventNotFound() {
            Long eventId = 999L;
            when(policyCacheRepository.find(eventId)).thenReturn(Optional.empty());
            when(policyRepository.findByEventId(eventId)).thenReturn(Optional.empty());
            when(eventRepository.existsById(eventId)).thenReturn(false);

            assertThatThrownBy(() -> policyService.getPolicyByEventId(eventId))
                    .isInstanceOf(CustomException.class)
                    .satisfies(ex -> assertThat(((CustomException) ex).getErrorCode())
                            .isEqualTo(EventErrorCode.EVENT_NOT_FOUND));
            verify(policyCacheRepository).find(eventId);
            verify(policyRepository).findByEventId(eventId);
            verify(eventRepository).existsById(eventId);
        }

        @Test
        @DisplayName("event는 있으나 캐시·DB 모두 정책이 없으면 negative 캐시 저장 후 POLICY_NOT_FOUND 예외가 발생한다")
        void getPolicyByEventId_policyNotExists_savesNegativeAndThrows() {
            Long eventId = 1L;
            when(eventRepository.existsById(eventId)).thenReturn(true);
            when(policyCacheRepository.find(eventId)).thenReturn(Optional.empty());
            when(policyRepository.findByEventId(eventId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> policyService.getPolicyByEventId(eventId))
                    .isInstanceOf(CustomException.class)
                    .satisfies(ex -> assertThat(((CustomException) ex).getErrorCode())
                            .isEqualTo(PolicyErrorCode.POLICY_NOT_FOUND));
            verify(policyCacheRepository).save(eq(eventId), org.mockito.ArgumentMatchers.argThat(
                    dto -> !dto.exists() && eventId.equals(dto.eventId())));
        }

        @Test
        @DisplayName("negative 캐시 히트(정책 없음)면 DB 조회 없이 POLICY_NOT_FOUND를 던진다")
        void getPolicyByEventId_negativeCacheHit_throwsPolicyNotFound() {
            Long eventId = 1L;
            PolicyCacheDto negativeCached = PolicyCacheDto.negative(eventId);
            when(policyCacheRepository.find(eventId)).thenReturn(Optional.of(negativeCached));

            assertThatThrownBy(() -> policyService.getPolicyByEventId(eventId))
                    .isInstanceOf(CustomException.class)
                    .satisfies(ex -> assertThat(((CustomException) ex).getErrorCode())
                            .isEqualTo(PolicyErrorCode.POLICY_NOT_FOUND));
            verify(policyRepository, never()).findByEventId(any());
            verify(eventRepository, never()).existsById(any());

        }
    }

    @Nested
    @DisplayName("정책 변경")
    class UpdatePolicy {

        @Test
        @DisplayName("event가 없으면 EVENT_NOT_FOUND 예외가 발생한다")
        void updatePolicy_eventNotExists_throwsEventNotFound() {
            Long eventId = 999L;
            PolicyUpdateRequest request = new PolicyUpdateRequest(10, 5, 300L, BlockRules.empty());
            when(eventRepository.existsById(eventId)).thenReturn(false);

            assertThatThrownBy(() -> policyService.updatePolicy(eventId, request, null))
                    .isInstanceOf(CustomException.class)
                    .satisfies(ex -> assertThat(((CustomException) ex).getErrorCode())
                            .isEqualTo(EventErrorCode.EVENT_NOT_FOUND));
            verify(policyRepository, never()).findByEventId(any());
        }

        @Test
        @DisplayName("정책이 없으면 새로 생성하고 버전 1을 반환한다")
        void updatePolicy_noPolicy_createsAndReturnsVersion1() {
            Long eventId = 1L;
            PolicyUpdateRequest request = new PolicyUpdateRequest(10, 5, 300L, BlockRules.empty());
            when(eventRepository.existsById(eventId)).thenReturn(true);
            when(policyRepository.findByEventId(eventId)).thenReturn(Optional.empty());
            when(policyRepository.saveAndFlush(any(Policy.class))).thenAnswer(inv -> inv.getArgument(0));

            Long version = policyService.updatePolicy(eventId, request, null);

            assertThat(version).isEqualTo(1L);
            verify(historyRepository, never()).save(any());
            verify(policyRepository).findByEventId(eventId);
            verify(policyRepository).saveAndFlush(any(Policy.class));
            verify(policyCacheRepository).save(eq(eventId), any());
        }

        @Test
        @DisplayName("정책이 있으면 이력 저장 후 버전을 반환한다")
        void updatePolicy_exists_savesHistoryAndReturnsVersion() {
            Long eventId = 1L;
            Policy policy = new Policy(eventId);
            PolicyUpdateRequest request = new PolicyUpdateRequest(20, 10, 600L, BlockRules.empty());
            when(eventRepository.existsById(eventId)).thenReturn(true);
            when(policyRepository.findByEventId(eventId)).thenReturn(Optional.of(policy));
            when(policyRepository.saveAndFlush(any(Policy.class))).thenAnswer(inv -> inv.getArgument(0));

            Long version = policyService.updatePolicy(eventId, request, null);

            assertThat(version).isEqualTo(1L);
            verify(historyRepository).save(any());
            verify(policyRepository).saveAndFlush(any(Policy.class));
            verify(policyCacheRepository).save(eq(eventId), any());
        }

        @Test
        @DisplayName("동시 수정으로 낙관적 락 충돌 시 POLICY_CONFLICT 예외가 발생한다")
        void updatePolicy_optimisticLockConflict_throwsPolicyConflict() {
            Long eventId = 1L;
            Policy policy = new Policy(eventId);
            PolicyUpdateRequest request = new PolicyUpdateRequest(20, 10, 600L, BlockRules.empty());
            when(eventRepository.existsById(eventId)).thenReturn(true);
            when(policyRepository.findByEventId(eventId)).thenReturn(Optional.of(policy));
            when(policyRepository.saveAndFlush(any(Policy.class))).thenThrow(new OptimisticLockException());

            assertThatThrownBy(() -> policyService.updatePolicy(eventId, request, null))
                    .isInstanceOf(CustomException.class)
                    .satisfies(ex -> assertThat(((CustomException) ex).getErrorCode())
                            .isEqualTo(PolicyErrorCode.POLICY_CONFLICT));
        }
    }
}
