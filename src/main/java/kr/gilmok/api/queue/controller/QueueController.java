package kr.gilmok.api.queue.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import kr.gilmok.api.policy.dto.PolicyCacheDto;
import kr.gilmok.api.policy.filter.PolicyFilter;
import kr.gilmok.api.queue.dto.QueueRegisterRequest;
import lombok.extern.slf4j.Slf4j;
import kr.gilmok.api.queue.dto.QueueRegisterResponse;
import kr.gilmok.api.queue.dto.QueueStatusResponse;
import kr.gilmok.api.queue.service.QueueService;
import kr.gilmok.common.dto.ApiResponse;
import kr.gilmok.common.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/queue")
@RequiredArgsConstructor
@Tag(name = "Queue", description = "대기열 API")
@SecurityRequirement(name = "bearerAuth")
public class QueueController {

    private final QueueService queueService;

    @PostMapping("/register")
    @Operation(summary = "대기열 등록", description = "이벤트 대기열에 사용자를 등록하고 큐 키를 발급합니다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "등록 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "요청 값 검증 실패"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "503", description = "Redis 장애")
    })
    public ResponseEntity<ApiResponse<QueueRegisterResponse>> register(
            @AuthenticationPrincipal CustomUserDetails principal,
            @Valid @RequestBody QueueRegisterRequest request,
            HttpServletRequest httpRequest) {
        Long userId = principal.user().id();
        // PolicyFilter가 정책 조회 결과를 request attribute로 전달 (eventId 미추출·정책 미존재 시 null)
        PolicyCacheDto policy = (PolicyCacheDto) httpRequest.getAttribute(PolicyFilter.POLICY_CACHE_ATTR);
        if (policy == null) {
            log.debug("policyCache not set by PolicyFilter, falling back to default RPS");
        }
        QueueRegisterResponse response = queueService.register(userId, request, policy);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @GetMapping("/status")
    @Operation(summary = "대기열 상태 조회", description = "queueKey와 eventId로 현재 대기 순번 및 상태를 조회합니다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "대기열 정보 없음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "503", description = "Redis 장애")
    })
    public ResponseEntity<ApiResponse<QueueStatusResponse>> getStatus(
            @AuthenticationPrincipal CustomUserDetails principal,
            @RequestParam String eventId,
            @RequestHeader("X-Queue-Key") String queueKey) {
        QueueStatusResponse response = queueService.getStatus(eventId, queueKey, principal.getUsername(), principal.user().id());
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
