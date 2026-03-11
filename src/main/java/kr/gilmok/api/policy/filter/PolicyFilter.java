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
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class PolicyFilter extends OncePerRequestFilter {

    private static final String QUEUE_PATH_PREFIX = "/queue/";
    private static final String BLOCK_KEY_PREFIX = "policy:block:";
    private static final String RATE_LIMIT_KEY_PREFIX = "policy:rl:";
    private static final ConcurrentHashMap<String, Pattern> PATTERN_CACHE = new ConcurrentHashMap<>();
    private static final java.util.Set<String> INVALID_PATTERNS = java.util.Collections
            .newSetFromMap(new ConcurrentHashMap<>());

    private final PolicyCacheRepository policyCacheRepository;
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path == null || !path.startsWith(QUEUE_PATH_PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        HttpServletRequest requestToUse = request;
        if ("POST".equalsIgnoreCase(request.getMethod()) && request.getRequestURI().contains("/queue/register")) {
            requestToUse = new CachedBodyHttpServletRequestWrapper(request);
        }

        Long eventId = extractEventId(requestToUse);
        if (eventId == null) {
            filterChain.doFilter(requestToUse, response);
            return;
        }

        Optional<PolicyCacheDto> policyOpt = policyCacheRepository.find(eventId);
        if (policyOpt.isEmpty() || !policyOpt.get().exists()) {
            filterChain.doFilter(requestToUse, response);
            return;
        }

        PolicyCacheDto policy = policyOpt.get();
        String clientIp = getClientIp(requestToUse);
        String userAgent = requestToUse.getHeader("User-Agent");
        String clientKey = resolveClientKey(clientIp);

        // 1. BlockRules (IP / User-Agent)
        if (blockedByRules(policy.blockRules(), clientIp, userAgent)) {
            log.warn("[PolicyFilter] Blocked by rules: eventId={}, ip={}", eventId, maskIp(clientIp));
            sendError(response, HttpServletResponse.SC_FORBIDDEN, "Access denied by policy");
            return;
        }

        // 2. 이미 차단된 클라이언트인지
        String blockKey = BLOCK_KEY_PREFIX + eventId + ":" + clientKey;
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
                int blockMinutes = Math.max(1, policy.blockDurationMinutes());
                redisTemplate.opsForValue().set(blockKey, "1", blockMinutes, TimeUnit.MINUTES);
                log.warn(
                        "[PolicyFilter] Rate limit exceeded, blocking: eventId={}, key={}, count={}, maxRps={}, blockMin={}",
                        eventId, clientKey, count, maxRps, blockMinutes);
                sendError(response, 429, "Too many requests; temporarily blocked");
                return;
            }
        }

        filterChain.doFilter(requestToUse, response);
    }

    private Long extractEventId(HttpServletRequest request) {
        if ("GET".equalsIgnoreCase(request.getMethod())) {
            String eventId = request.getParameter("eventId");
            return parseEventId(eventId);
        }
        if ("POST".equalsIgnoreCase(request.getMethod()) && request.getRequestURI().contains("/queue/register")) {
            try {
                byte[] body = request.getInputStream().readAllBytes();
                if (body.length == 0)
                    return null;
                JsonNode node = objectMapper.readTree(body);
                JsonNode idNode = node != null ? node.get("eventId") : null;
                return idNode != null ? parseEventId(idNode.asText()) : null;
            } catch (Exception e) {
                log.debug("[PolicyFilter] Failed to parse eventId from body", e);
                return null;
            }
        }
        return null;
    }

    private Long parseEventId(String value) {
        if (value == null || value.isBlank())
            return null;
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private boolean blockedByRules(BlockRules rules, String clientIp, String userAgent) {
        if (rules == null)
            return false;
        if (rules.ipPattern() != null && !rules.ipPattern().isBlank()) {
            Pattern compiled = compilePattern(rules.ipPattern());
            if (compiled != null && compiled.matcher(clientIp != null ? clientIp : "").matches()) {
                return true;
            }
        }
        if (rules.userAgentPattern() != null && !rules.userAgentPattern().isBlank()) {
            Pattern compiled = compilePattern(rules.userAgentPattern());
            if (compiled != null && compiled.matcher(userAgent != null ? userAgent : "").matches()) {
                return true;
            }
        }
        return false;
    }

    private Pattern compilePattern(String regex) {
        if (INVALID_PATTERNS.contains(regex))
            return null;
        return PATTERN_CACHE.computeIfAbsent(regex, r -> {
            try {
                return Pattern.compile(r);
            } catch (Exception e) {
                log.debug("[PolicyFilter] Invalid pattern, skipping: {}", r, e);
                INVALID_PATTERNS.add(r);
                return null; // computeIfAbsent: null이면 map에 저장 안 됨 → 매번 재시도 방지를 INVALID_PATTERNS로 처리
            }
        });
    }

    private String resolveClientKey(String clientIp) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof CustomUserDetails details) {
            return "u:" + details.user().id();
        }
        return "ip:" + (clientIp != null ? clientIp : "unknown");
    }

    private String getClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private String maskIp(String ip) {
        if (ip == null || ip.length() < 8)
            return "***";
        return ip.substring(0, ip.length() / 2) + "***";
    }

    private void sendError(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"message\":\"" + message.replace("\"", "\\\"") + "\"}");
    }
}
