package kr.gilmok.api.policy.controller;

import jakarta.validation.Valid;
import kr.gilmok.api.policy.dto.AiRecommendationResponse;
import kr.gilmok.api.policy.dto.MetricsResponse;
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
public class AdminPolicyController {

    private final PolicyService policyService;

    @GetMapping("/policy")
    public ApiResponse<PolicyResponse> getPolicy(@PathVariable Long eventId) {
        PolicyResponse response = policyService.getPolicyByEventId(eventId);
        return ApiResponse.success(response);
    }

    @PutMapping("/policy")
    public ApiResponse<Long> updatePolicy(
            @AuthenticationPrincipal CustomUserDetails principal,
            @PathVariable Long eventId,
            @Valid @RequestBody PolicyUpdateRequest request) {

        Long updatedByUserId = principal.user().id();
        Long newVersion = policyService.updatePolicy(eventId, request, updatedByUserId);
        return ApiResponse.success(newVersion);
    }

    @GetMapping("/metrics")
    public ApiResponse<MetricsResponse> getMetrics(@PathVariable Long eventId) {
        MetricsResponse response = policyService.getMetrics(eventId);
        return ApiResponse.success(response);
    }

    @GetMapping("/ai")
    public ApiResponse<AiRecommendationResponse> getAiRecommendation(@PathVariable Long eventId) {
        AiRecommendationResponse response = policyService.getAiRecommendation(eventId);
        return ApiResponse.success(response);
    }
}
