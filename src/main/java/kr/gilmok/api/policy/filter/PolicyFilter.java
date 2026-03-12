package kr.gilmok.api.policy.filter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import kr.gilmok.api.policy.dto.PolicyCacheDto;
import kr.gilmok.api.policy.repository.PolicyCacheRepository;
import kr.gilmok.api.policy.vo.BlockRules;
import kr.gilmok.common.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class PolicyFilter extends OncePerRequestFilter {

    private static final String QUEUE_PATH_PREFIX = "/queue/";
    private static final String BLOCK_KEY_PREFIX = "policy:block:";
    private static final String RATE_LIMIT_KEY_PREFIX = "policy:rl:";

    // 무제한 메모리 점유 방지를 위해 크기가 제한된 LRU 캐시 사용 (최대 500개 패턴)
    private static final int MAX_PATTERN_CACHE_SIZE = 500;
    private static final Map<String, Pattern> PATTERN_CACHE = Collections.synchronizedMap(
            new LinkedHashMap<String, Pattern>(MAX_PATTERN_CACHE_SIZE, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Pattern> eldest) {
                    return size() > MAX_PATTERN_CACHE_SIZE;
                }
            }
    );

    private static final Set<String> INVALID_PATTERNS = Collections.synchronizedSet(
            Collections.newSetFromMap(
                    new LinkedHashMap<String, Boolean>(100, 0.75f, true) {
                        @Override
                        protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
                            return size() > 100;
                        }
                    }
            )
    );

    /** ReDoS 방지: 정규식 매칭 타임아웃(ms). 초과 시 매칭 실패로 간주. */
    private static final long REGEX_MATCH_TIMEOUT_MS = 200;
    private static final int REGEX_MATCH_QUEUE_CAPACITY = 256;
    private static final ExecutorService REGEX_MATCH_EXECUTOR = new ThreadPoolExecutor(
            4, 4, 0L, TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<>(REGEX_MATCH_QUEUE_CAPACITY),
            new ThreadPoolExecutor.AbortPolicy()
    );

    /** 타임아웃 발생한 정규식: 재제출 방지용. PATTERN_CACHE와 별도 관리. */
    private static final Set<String> TIMEOUT_PATTERNS = Collections.synchronizedSet(
            Collections.newSetFromMap(
                    new LinkedHashMap<String, Boolean>(100, 0.75f, true) {
                        @Override
                        protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
                            return size() > 100;
                        }
                    }
            )
    );

    private final PolicyCacheRepository policyCacheRepository;
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    /** true일 때만 X-Forwarded-For 헤더를 신뢰. 기본값 false(프록시 미신뢰, getRemoteAddr 사용). */
    @Value("${app.policy.trust-forwarded-for:false}")
    private boolean trustForwardedFor;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();
        if (!path.startsWith(QUEUE_PATH_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        CachedBodyHttpServletRequestWrapper wrappedRequest;
        try {
            wrappedRequest = new CachedBodyHttpServletRequestWrapper(request);
        } catch (IOException e) {
            log.warn("[PolicyFilter] Request body limit exceeded: {}", e.getMessage());
            sendError(response, 413, "Request entity too large");
            return;
        }

        Long eventId = extractEventId(wrappedRequest, response);
        if (eventId == null) {
            if (!response.isCommitted()) {
                filterChain.doFilter(wrappedRequest, response);
            }
            return;
        }

        Optional<PolicyCacheDto> policyOpt = policyCacheRepository.find(eventId);
        if (policyOpt.isEmpty() || !policyOpt.get().exists()) {
            filterChain.doFilter(wrappedRequest, response);
            return;
        }

        PolicyCacheDto policy = policyOpt.get();
        String clientIp = getClientIp(wrappedRequest);
        String userAgent = wrappedRequest.getHeader("User-Agent");
        String clientKey = resolveClientKey(clientIp);
        String blockKey = BLOCK_KEY_PREFIX + eventId + ":" + clientKey;

        // 1. 차단 규칙 확인 (Regex) — Redis 외 로직, 예외 시 그대로 전파
        if (blockedByRules(policy.blockRules(), clientIp, userAgent)) {
            log.info("[PolicyFilter] Request blocked by rules: eventId={}, ip={}, ua={}", eventId, maskIp(clientIp), userAgent);
            sendError(response, 403, "Access denied by security policy");
            return;
        }

        try {
            // 2. 임시 차단 여부 확인 (Redis)
            if (Boolean.TRUE.equals(redisTemplate.hasKey(blockKey))) {
                log.debug("[PolicyFilter] Request from blocked client: eventId={}, key={}", eventId, clientKey);
                sendError(response, 429, "Too many requests; temporarily blocked");
                return;
            }

            // 3. Rate limit (maxRequestsPerSecond) — Redis만 사용
            int maxRps = policy.maxRequestsPerSecond();
            if (maxRps > 0) {
                long currentSecond = System.currentTimeMillis() / 1000;
                String rlKey = RATE_LIMIT_KEY_PREFIX + eventId + ":" + clientKey + ":" + currentSecond;
                Long count = redisTemplate.opsForValue().increment(rlKey);

                if (count != null && count == 1) {
                    redisTemplate.expire(rlKey, 2, TimeUnit.SECONDS);
                }

                if (count != null && count > maxRps) {
                    int blockMinutes = policy.blockDurationMinutes();
                    if (blockMinutes > 0) {
                        redisTemplate.opsForValue().set(blockKey, "1", blockMinutes, TimeUnit.MINUTES);
                    }
                    log.warn("[PolicyFilter] Rate limit exceeded, blocking: eventId={}, key={}, count={}, maxRps={}, blockMin={}",
                            eventId, clientKey, count, maxRps, blockMinutes);
                    sendError(response, 429, "Too many requests; temporarily blocked");
                    return;
                }
            }
        } catch (DataAccessException e) {
            // Redis 장애 시에만 Fail-Open (차단/rate limit 건너뛰고 통과)
            log.error("[PolicyFilter] Redis error during policy enforcement (Fail-Open): eventId={}", eventId, e);
        }

        filterChain.doFilter(wrappedRequest, response);
    }

    /**
     * eventId 추출. POST는 body 우선(정책 우회 방지); query와 body 둘 다 있으면 불일치 시 400.
     */
    private Long extractEventId(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Long fromQuery = parseEventIdParam(request.getParameter("eventId"));
        boolean isPostWithBody = "POST".equalsIgnoreCase(request.getMethod())
                && request.getRequestURI() != null && request.getRequestURI().contains("/queue/register");
        Long fromBody = null;
        if (isPostWithBody && request instanceof CachedBodyHttpServletRequestWrapper wrapper) {
            try {
                byte[] body = wrapper.getInputStream().readAllBytes();
                if (body.length > 0) {
                    JsonNode node = objectMapper.readTree(body);
                    if (node.has("eventId") && node.get("eventId").isNumber()) {
                        fromBody = node.get("eventId").asLong();
                    }
                }
            } catch (Exception ignored) {}
        }

        if (fromBody != null) {
            if (fromQuery != null && !fromBody.equals(fromQuery)) {
                sendError(response, 400, "eventId mismatch: query and body must match");
                return null;
            }
            return fromBody;
        }
        return fromQuery;
    }

    private Long parseEventIdParam(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private boolean blockedByRules(BlockRules rules, String ip, String ua) {
        if (rules == null) return false;
        if (rules.ipPattern() != null && ip != null) {
            if (matches(rules.ipPattern(), ip)) return true;
        }
        if (rules.userAgentPattern() != null && ua != null) {
            if (matches(rules.userAgentPattern(), ua)) return true;
        }
        return false;
    }

    private boolean matches(String regex, String input) {
        if (regex == null || regex.isBlank() || INVALID_PATTERNS.contains(regex) || TIMEOUT_PATTERNS.contains(regex)) {
            return false;
        }
        Pattern p = PATTERN_CACHE.get(regex);
        if (p == null) {
            final String finalRegex = regex;
            Future<Pattern> compileFuture;
            try {
                compileFuture = REGEX_MATCH_EXECUTOR.submit(() -> Pattern.compile(finalRegex));
            } catch (RejectedExecutionException e) {
                log.warn("[PolicyFilter] Regex executor queue full, skipping compile");
                return false;
            }
            try {
                p = compileFuture.get(REGEX_MATCH_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                PATTERN_CACHE.put(regex, p);
            } catch (TimeoutException e) {
                compileFuture.cancel(true);
                TIMEOUT_PATTERNS.add(regex);
                log.warn("[PolicyFilter] Regex compile timeout, regex length={}", regex.length());
                return false;
            } catch (Exception e) {
                log.debug("[PolicyFilter] Invalid regex pattern: {}", regex);
                INVALID_PATTERNS.add(regex);
                return false;
            }
        }
        final Pattern finalP = p;
        // ReDoS 방지: 타임아웃 내에서만 매칭 수행
        Future<Boolean> future;
        try {
            future = REGEX_MATCH_EXECUTOR.submit(() -> finalP.matcher(input).find());
        } catch (RejectedExecutionException e) {
            log.warn("[PolicyFilter] Regex executor queue full, skipping match");
            return false;
        }
        try {
            return future.get(REGEX_MATCH_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            TIMEOUT_PATTERNS.add(regex);
            log.warn("[PolicyFilter] Regex match timeout (ReDoS?), regex length={}, input length={}", regex.length(), input != null ? input.length() : 0);
            return false;
        } catch (Exception e) {
            log.debug("[PolicyFilter] Regex match error", e);
            return false;
        }
    }

    private String resolveClientKey(String clientIp) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof CustomUserDetails details) {
            return "u:" + details.user().id();
        }
        return "ip:" + (clientIp != null ? clientIp : "unknown");
    }

    private String getClientIp(HttpServletRequest request) {
        if (!trustForwardedFor) {
            return request.getRemoteAddr();
        }
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private String maskIp(String ip) {
        if (ip == null || ip.length() < 4) return "***";
        return ip.substring(0, ip.length() / 2) + "***";
    }

    private void sendError(HttpServletResponse response, int status, String message) throws IOException {
        if (!response.isCommitted()) {
            response.setStatus(status);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(objectMapper.writeValueAsString(Map.of("message", message)));
        }
    }
}