package kr.gilmok.api.queue.controller;

import jakarta.validation.Valid;
import kr.gilmok.api.queue.dto.QueueRegisterRequest;
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

@RestController
@RequestMapping("/queue")
@RequiredArgsConstructor
public class QueueController {

    private final QueueService queueService;

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<QueueRegisterResponse>> register(
            @AuthenticationPrincipal CustomUserDetails principal,
            @Valid @RequestBody QueueRegisterRequest request) {
        Long userId = principal.user().id();
        QueueRegisterResponse response = queueService.register(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @GetMapping("/status")
    public ResponseEntity<ApiResponse<QueueStatusResponse>> getStatus(
            @RequestParam String eventId,
            @RequestHeader("X-Queue-Key") String queueKey) {
        QueueStatusResponse response = queueService.getStatus(eventId, queueKey);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
