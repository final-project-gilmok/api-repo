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
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.TimeUnit;
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

    private static final Set<String> INVALID_PATTERNS = Collections.newSetFromMap(
            new LinkedHashMap<String, Boolean>(100, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
                    return size() > 100;
                }
            }
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

        Long eventId = extractEventId(wrappedRequest);
        if (eventId == null) {
            filterChain.doFilter(wrappedRequest, response);
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
            // 1. 차단 규칙 확인 (Regex)
            if (blockedByRules(policy.blockRules(), clientIp, userAgent)) {
                log.info("[PolicyFilter] Request blocked by rules: eventId={}, ip={}, ua={}", eventId, maskIp(clientIp), userAgent);
                sendError(response, 403, "Access denied by security policy");
                return;
            }

            // 2. 임시 차단 여부 확인 (Redis)
            if (Boolean.TRUE.equals(redisTemplate.hasKey(blockKey))) {
                log.debug("[PolicyFilter] Request from blocked client: eventId={}, key={}", eventId, clientKey);
                sendError(response, 429, "Too many requests; temporarily blocked");
                return;
            }

            // 3. Rate limit (maxRequestsPerSecond)
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
                    // 0분이면 차단 키 저장을 건너뜀
                    if (blockMinutes > 0) {
                        redisTemplate.opsForValue().set(blockKey, "1", blockMinutes, TimeUnit.MINUTES);
                    }
                    log.warn("[PolicyFilter] Rate limit exceeded, blocking: eventId={}, key={}, count={}, maxRps={}, blockMin={}",
                            eventId, clientKey, count, maxRps, blockMinutes);
                    sendError(response, 429, "Too many requests; temporarily blocked");
                    return;
                }
            }
        } catch (Exception e) {
            // Redis 장애 시 서비스 중단을 막기 위한 Fail-Open 전략
            log.error("[PolicyFilter] Error during policy enforcement (Fail-Open): {}", e.getMessage());
        }

        filterChain.doFilter(wrappedRequest, response);
    }

    private Long extractEventId(HttpServletRequest request) {
        String eventIdParam = request.getParameter("eventId");
        if (eventIdParam != null) {
            try { return Long.parseLong(eventIdParam); } catch (NumberFormatException ignored) {}
        }
        try {
            byte[] body = ((CachedBodyHttpServletRequestWrapper) request).getInputStream().readAllBytes();
            JsonNode node = objectMapper.readTree(body);
            if (node.has("eventId")) return node.get("eventId").asLong();
        } catch (Exception ignored) {}
        return null;
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
        if (regex == null || regex.isBlank() || INVALID_PATTERNS.contains(regex)) return false;
        Pattern p = PATTERN_CACHE.get(regex);
        if (p == null) {
            try {
                p = Pattern.compile(regex);
                PATTERN_CACHE.put(regex, p);
            } catch (Exception e) {
                log.debug("[PolicyFilter] Invalid regex pattern: {}", regex);
                INVALID_PATTERNS.add(regex);
                return false;
            }
        }
        return p.matcher(input).find();
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
            response.getWriter().write("{\"message\":\"" + message.replace("\"", "\\\"") + "\"}");
        }
    }
}