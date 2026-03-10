package kr.gilmok.api.policy.controller;

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
public class AdminPolicyController {

    private final PolicyService policyService;

    @GetMapping("/policy")
    public ApiResponse<PolicyResponse> getPolicy(@PathVariable Long eventId) {
        PolicyResponse response = policyService.getPolicyByEventId(eventId);
        return ApiResponse.success(response);
    }

    @PutMapping("/policy")
    public ApiResponse<PolicyResponse> updatePolicy(
            @AuthenticationPrincipal CustomUserDetails principal,
            @PathVariable Long eventId,
            @Valid @RequestBody PolicyUpdateRequest request) {

        Long updatedByUserId = principal.user().id();
        PolicyResponse response = policyService.updatePolicy(eventId, request, updatedByUserId);
        return ApiResponse.success(response);
    }
}
