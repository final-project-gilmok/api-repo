package kr.gilmok.api.queue.controller;

import jakarta.validation.Valid;
import kr.gilmok.api.queue.dto.QueueRegisterRequest;
import kr.gilmok.api.queue.dto.QueueRegisterResponse;
import kr.gilmok.api.queue.dto.QueueStatusResponse;
import kr.gilmok.api.queue.service.QueueService;
import kr.gilmok.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/queue")
@RequiredArgsConstructor
public class QueueController {

    private final QueueService queueService;

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<QueueRegisterResponse>> register(
            @Valid @RequestBody QueueRegisterRequest request) {
        QueueRegisterResponse response = queueService.register(request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/status")
    public ResponseEntity<ApiResponse<QueueStatusResponse>> getStatus(
            @RequestParam String eventId,
            @RequestParam String queueKey) {
        QueueStatusResponse response = queueService.getStatus(eventId, queueKey);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
