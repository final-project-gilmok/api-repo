package kr.gilmok.api.policy.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import kr.gilmok.api.policy.dto.PolicyResponse;
import kr.gilmok.api.policy.dto.PolicyUpdateRequest;
import kr.gilmok.api.policy.service.PolicyService;
import kr.gilmok.common.dto.ApiResponse;
import kr.gilmok.common.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/events/{eventId}")
@RequiredArgsConstructor
@Tag(name = "Admin Policy", description = "관리자 이벤트 정책 관리 API")
@SecurityRequirement(name = "bearerAuth")
public class AdminPolicyController {

    private final PolicyService policyService;

    @GetMapping("/policy")
    @Operation(summary = "정책 조회", description = "eventId에 해당하는 이벤트 정책을 조회합니다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "이벤트 또는 정책 없음")
    })
    public ApiResponse<PolicyResponse> getPolicy(@PathVariable Long eventId) {
        PolicyResponse response = policyService.getPolicyByEventId(eventId);
        return ApiResponse.success(response);
    }

    @PutMapping("/policy")
    @Operation(summary = "정책 수정", description = "eventId에 해당하는 이벤트 정책을 수정하고 수정된 정책을 반환합니다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "수정 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "요청 값 검증 실패"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "이벤트 또는 정책 없음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "동시 수정 충돌")
    })
    public ApiResponse<PolicyResponse> updatePolicy(
            @AuthenticationPrincipal CustomUserDetails principal,
            @PathVariable Long eventId,
            @Valid @RequestBody PolicyUpdateRequest request) {

        Long updatedByUserId = principal.user().id();
        PolicyResponse response = policyService.updatePolicy(eventId, request, updatedByUserId);
        return ApiResponse.success(response);
    }
}
