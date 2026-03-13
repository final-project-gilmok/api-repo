package kr.gilmok.api.policy.filter;

import jakarta.servlet.FilterChain;
import kr.gilmok.api.policy.dto.PolicyCacheDto;
import kr.gilmok.api.policy.repository.PolicyCacheRepository;
import kr.gilmok.api.policy.vo.BlockRules;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PolicyFilterTest {

    @Mock
    private PolicyCacheRepository policyCacheRepository;

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private FilterChain filterChain;

    private CountDownLatch releaseLatch;
    private List<Future<?>> saturatedFutures = new ArrayList<>();

    @AfterEach
    void tearDown() throws Exception {
        if (releaseLatch != null) {
            releaseLatch.countDown();
        }

        for (Future<?> future : saturatedFutures) {
            future.cancel(true);
        }

        ThreadPoolExecutor executor = getRegexExecutor();
        executor.getQueue().clear();
    }

    @Test
    @DisplayName("보안 검사 executor 포화 시 429를 반환하고 요청을 차단한다")
    void doFilter_executorRejected_returns429AndDoesNotCallChain() throws Exception {
        // given
        PolicyFilter policyFilter = new PolicyFilter(
                policyCacheRepository,
                redisTemplate,
                new com.fasterxml.jackson.databind.ObjectMapper()
        );

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/queue/register");
        request.setParameter("eventId", "1");
        request.addHeader("User-Agent", "Mozilla/5.0");
        request.setRemoteAddr("127.0.0.1");

        MockHttpServletResponse response = new MockHttpServletResponse();

        PolicyCacheDto policy = mock(PolicyCacheDto.class);
        BlockRules blockRules = mock(BlockRules.class);

        when(policyCacheRepository.find(1L)).thenReturn(Optional.of(policy));
        when(policy.exists()).thenReturn(true);
        when(policy.blockRules()).thenReturn(blockRules);
        when(blockRules.ipPattern()).thenReturn("127\\.0\\.0\\.1");
        // userAgentPattern 스텁 제거: executor 포화 시 IP 검사 제출에서 바로 RejectedExecutionException 발생해 UA 검사까지 도달하지 않음

        when(redisTemplate.hasKey("policy:block:1:ip:127.0.0.1")).thenReturn(false);

        saturateRegexExecutor();

        // when
        policyFilter.doFilter(request, response, filterChain);

        // then
        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getContentAsString()).contains("Security inspection overloaded");
        verify(filterChain, never()).doFilter(any(), any());
    }

    private void saturateRegexExecutor() throws Exception {
        ThreadPoolExecutor executor = getRegexExecutor();
        releaseLatch = new CountDownLatch(1);

        int poolSize = executor.getCorePoolSize();
        int queueCapacity = getRegexQueueCapacity();

        for (int i = 0; i < poolSize + queueCapacity; i++) {
            Future<?> future = executor.submit(() -> {
                try {
                    releaseLatch.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            saturatedFutures.add(future);
        }

        assertThat(executor.getActiveCount()).isEqualTo(poolSize);
        assertThat(executor.getQueue().remainingCapacity()).isEqualTo(0);
    }

    private ThreadPoolExecutor getRegexExecutor() throws Exception {
        Field field = PolicyFilter.class.getDeclaredField("REGEX_MATCH_EXECUTOR");
        field.setAccessible(true);
        ExecutorService executorService = (ExecutorService) field.get(null);
        return (ThreadPoolExecutor) executorService;
    }

    private int getRegexQueueCapacity() throws Exception {
        Field field = PolicyFilter.class.getDeclaredField("REGEX_MATCH_QUEUE_CAPACITY");
        field.setAccessible(true);
        return field.getInt(null);
    }
}