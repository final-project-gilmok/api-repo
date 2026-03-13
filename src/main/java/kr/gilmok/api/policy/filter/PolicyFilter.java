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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

@Slf4j
@Component
@RequiredArgsConstructor
public class PolicyFilter extends OncePerRequestFilter {

    private static final String QUEUE_PATH_PREFIX = "/queue/";
    private static final String QUEUE_REGISTER_PATH = "/queue/register";

    private static final String BLOCK_KEY_PREFIX = "policy:block:";
    private static final String RATE_LIMIT_KEY_PREFIX = "policy:rl:";
    private static final String REGEX_TIMEOUT_KEY_PREFIX = "policy:regex-timeout:";

    private static final int MAX_PATTERN_CACHE_SIZE = 500;

    /** ReDoS 방지용 정규식 매칭 타임아웃 */
    private static final long REGEX_MATCH_TIMEOUT_MS = 200L;

    /** 정규식 작업 큐 제한 */
    private static final int REGEX_MATCH_QUEUE_CAPACITY = 256;

    /** timeout 누적 윈도우 */
    private static final long REGEX_TIMEOUT_WINDOW_SECONDS = 30L;

    /** 같은 clientKey + ruleType 에서 timeout이 이 횟수 이상 발생하면 임시 차단 */
    private static final long REGEX_TIMEOUT_THRESHOLD = 5L;

    /** timeout 기반 임시 차단 시간 */
    private static final long REGEX_TIMEOUT_BLOCK_MINUTES = 1L;

    private static final ExecutorService REGEX_MATCH_EXECUTOR = new ThreadPoolExecutor(
            4,
            4,
            0L,
            TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<>(REGEX_MATCH_QUEUE_CAPACITY),
            new ThreadPoolExecutor.AbortPolicy()
    );

    private static final Map<String, Pattern> PATTERN_CACHE = Collections.synchronizedMap(
            new LinkedHashMap<String, Pattern>(MAX_PATTERN_CACHE_SIZE, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Pattern> eldest) {
                    return size() > MAX_PATTERN_CACHE_SIZE;
                }
            }
    );

    /** invalid pattern은 소량이면 충분하므로 단순 동시성 set으로 유지 */
    private static final Set<String> INVALID_PATTERNS = ConcurrentHashMap.newKeySet();

    private final PolicyCacheRepository policyCacheRepository;
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.policy.trust-forwarded-for:false}")
    private boolean trustForwardedFor;

    /**
     * true면 timeout이 발생한 민감 경로 요청은 즉시 차단.
     * 기본은 false 권장.
     */
    @Value("${app.policy.fail-close-on-timeout-for-sensitive-paths:false}")
    private boolean failCloseOnTimeoutForSensitivePaths;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();
        if (path == null || !path.startsWith(QUEUE_PATH_PREFIX)) {
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

        try {
            if (Boolean.TRUE.equals(redisTemplate.hasKey(blockKey))) {
                log.debug("[PolicyFilter] Request from blocked client: eventId={}, key={}", eventId, clientKey);
                sendError(response, 429, "Too many requests; temporarily blocked");
                return;
            }
        } catch (DataAccessException e) {
            log.error("[PolicyFilter] Redis error while checking block key (Fail-Open): eventId={}", eventId, e);
        }

        try {
            RuleCheckResult ruleResult = blockedByRules(eventId, clientKey, blockKey, policy.blockRules(), path, clientIp, userAgent);

            if (ruleResult.blocked()) {
                log.info("[PolicyFilter] Request blocked by rules: eventId={}, ip={}, ua={}",
                        eventId, maskIp(clientIp), userAgent);
                sendError(response, 403, "Access denied by security policy");
                return;
            }

            if (ruleResult.timedOut()) {
                if (ruleResult.escalated()) {
                    sendError(response, 429, "Suspicious request pattern detected");
                    return;
                }

                if (failCloseOnTimeoutForSensitivePaths && isSensitivePath(path)) {
                    log.warn("[PolicyFilter] Timeout on sensitive path, fail-close applied: eventId={}, path={}, ip={}",
                            eventId, path, maskIp(clientIp));
                    sendError(response, 403, "Request blocked by security policy");
                    return;
                }
            }
        } catch (SecurityThrottlingException e) {
            log.warn("[PolicyFilter] Security filter overloaded, fail-close: eventId={}, path={}", eventId, path, e);
            sendError(response, 429, "Security inspection overloaded");
            return;
        }

        try {
            int maxRps = policy.maxRequestsPerSecond();
            if (maxRps > 0) {
                long currentSecond = System.currentTimeMillis() / 1000;
                String rlKey = RATE_LIMIT_KEY_PREFIX + eventId + ":" + clientKey + ":" + currentSecond;

                Long count = redisTemplate.opsForValue().increment(rlKey);
                if (count != null && count == 1L) {
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
            log.error("[PolicyFilter] Redis error during policy enforcement (Fail-Open): eventId={}", eventId, e);
        }

        filterChain.doFilter(wrappedRequest, response);
    }

    /**
     * eventId 추출. POST는 body 우선. query와 body 둘 다 있으면 불일치 시 400.
     */
    private Long extractEventId(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Long fromQuery = parseEventIdParam(request.getParameter("eventId"));

        boolean isPostWithBody = "POST".equalsIgnoreCase(request.getMethod())
                && request.getRequestURI() != null
                && request.getRequestURI().startsWith(QUEUE_REGISTER_PATH);

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
            } catch (Exception ignored) {
                // body 파싱 실패는 여기서 막지 않음
            }
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
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private RuleCheckResult blockedByRules(Long eventId,
                                           String clientKey,
                                           String blockKey,
                                           BlockRules rules,
                                           String path,
                                           String ip,
                                           String ua) {
        if (rules == null) {
            return RuleCheckResult.notBlocked();
        }

        boolean timedOut = false;
        boolean escalated = false;

        if (rules.ipPattern() != null && ip != null) {
            MatchOutcome ipOutcome = matchWithTimeout(rules.ipPattern(), ip, MatchMode.FULL_MATCH);
            if (ipOutcome == MatchOutcome.MATCHED) {
                return RuleCheckResult.blockedResult();
            }
            if (ipOutcome == MatchOutcome.TIMED_OUT) {
                timedOut = true;
                escalated = handleRegexTimeout(eventId, clientKey, blockKey, "ip", path, ip, ua) || escalated;
            }
        }

        if (rules.userAgentPattern() != null && ua != null) {
            MatchOutcome uaOutcome = matchWithTimeout(rules.userAgentPattern(), ua, MatchMode.FIND);
            if (uaOutcome == MatchOutcome.MATCHED) {
                return RuleCheckResult.blockedResult();
            }
            if (uaOutcome == MatchOutcome.TIMED_OUT) {
                timedOut = true;
                escalated = handleRegexTimeout(eventId, clientKey, blockKey, "ua", path, ip, ua) || escalated;
            }
        }

        if (timedOut) {
            return RuleCheckResult.timedOutResult(escalated);
        }
        return RuleCheckResult.notBlocked();
    }

    private MatchOutcome matchWithTimeout(String regex, String input, MatchMode matchMode) {
        if (regex == null || regex.isBlank()) {
            return MatchOutcome.NOT_MATCHED;
        }

        if (INVALID_PATTERNS.contains(regex)) {
            return MatchOutcome.NOT_MATCHED;
        }

        Pattern pattern = getOrCompilePattern(regex);
        if (pattern == null) {
            return MatchOutcome.NOT_MATCHED;
        }

        final Pattern finalPattern = pattern;
        Future<Boolean> future;
        try {
            future = REGEX_MATCH_EXECUTOR.submit(() -> {
                if (input == null) {
                    return false;
                }
                return switch (matchMode) {
                    case FULL_MATCH -> finalPattern.matcher(input).matches();
                    case FIND -> finalPattern.matcher(input).find();
                };
            });
        } catch (RejectedExecutionException e) {
            throw new SecurityThrottlingException("Regex executor saturated", e);
        }

        try {
            Boolean matched = future.get(REGEX_MATCH_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            return Boolean.TRUE.equals(matched) ? MatchOutcome.MATCHED : MatchOutcome.NOT_MATCHED;
        } catch (TimeoutException e) {
            future.cancel(true);
            log.warn("[PolicyFilter] Regex match timeout: regexLength={}, inputLength={}, mode={}",
                    regex.length(),
                    input != null ? input.length() : 0,
                    matchMode);
            return MatchOutcome.TIMED_OUT;
        } catch (Exception e) {
            log.debug("[PolicyFilter] Regex match error", e);
            return MatchOutcome.NOT_MATCHED;
        }
    }

    private Pattern getOrCompilePattern(String regex) {
        Pattern pattern = PATTERN_CACHE.get(regex);
        if (pattern != null) {
            return pattern;
        }

        synchronized (PATTERN_CACHE) {
            pattern = PATTERN_CACHE.get(regex);
            if (pattern != null) {
                return pattern;
            }

            try {
                pattern = Pattern.compile(regex);
                PATTERN_CACHE.put(regex, pattern);
                return pattern;
            } catch (PatternSyntaxException e) {
                INVALID_PATTERNS.add(regex);
                log.debug("[PolicyFilter] Invalid regex pattern: {}", regex, e);
                return null;
            } catch (Exception e) {
                log.debug("[PolicyFilter] Regex compile error: {}", regex, e);
                return null;
            }
        }
    }

    /**
     * timeout 자체는 fail-open.
     * 다만 단기간 반복되면 클라이언트 임시 차단으로 승격.
     */
    private boolean handleRegexTimeout(Long eventId,
                                       String clientKey,
                                       String blockKey,
                                       String ruleType,
                                       String path,
                                       String clientIp,
                                       String userAgent) {
        String timeoutKey = REGEX_TIMEOUT_KEY_PREFIX + eventId + ":" + clientKey + ":" + ruleType;

        try {
            Long timeoutCount = redisTemplate.opsForValue().increment(timeoutKey);
            if (timeoutCount != null && timeoutCount == 1L) {
                redisTemplate.expire(timeoutKey, REGEX_TIMEOUT_WINDOW_SECONDS, TimeUnit.SECONDS);
            }

            log.warn("[PolicyFilter] Regex timeout detected: eventId={}, ruleType={}, path={}, key={}, ip={}, ua={}, timeoutCount={}",
                    eventId, ruleType, path, clientKey, maskIp(clientIp), userAgent, timeoutCount);

            if (timeoutCount != null && timeoutCount >= REGEX_TIMEOUT_THRESHOLD) {
                redisTemplate.opsForValue().set(
                        blockKey,
                        "1",
                        REGEX_TIMEOUT_BLOCK_MINUTES,
                        TimeUnit.MINUTES
                );

                log.warn("[PolicyFilter] Client temporarily blocked by repeated regex timeouts: eventId={}, ruleType={}, key={}, count={}",
                        eventId, ruleType, clientKey, timeoutCount);
                return true;
            }

            return false;
        } catch (DataAccessException e) {
            log.error("[PolicyFilter] Redis error while recording regex timeout (Fail-Open): eventId={}, ruleType={}",
                    eventId, ruleType, e);
            return false;
        }
    }

    private boolean isSensitivePath(String path) {
        if (path == null) {
            return false;
        }

        return path.startsWith("/queue/register")
                || path.startsWith("/admin")
                || path.equals("/login")
                || path.startsWith("/payment");
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
        if (ip == null || ip.length() < 4) {
            return "***";
        }
        return ip.substring(0, ip.length() / 2) + "***";
    }

    private void sendError(HttpServletResponse response, int status, String message) throws IOException {
        if (!response.isCommitted()) {
            response.setStatus(status);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(objectMapper.writeValueAsString(Map.of("message", message)));
        }
    }

    private enum MatchMode {
        FULL_MATCH,
        FIND
    }

    private enum MatchOutcome {
        MATCHED,
        NOT_MATCHED,
        TIMED_OUT
    }

    private record RuleCheckResult(boolean blocked, boolean timedOut, boolean escalated) {
        static RuleCheckResult blockedResult() {
            return new RuleCheckResult(true, false, false);
        }

        static RuleCheckResult timedOutResult(boolean escalated) {
            return new RuleCheckResult(false, true, escalated);
        }

        static RuleCheckResult notBlocked() {
            return new RuleCheckResult(false, false, false);
        }
    }

    private static class SecurityThrottlingException extends RuntimeException {
        SecurityThrottlingException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}