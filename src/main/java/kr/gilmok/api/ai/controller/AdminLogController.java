package kr.gilmok.api.ai.controller;

import kr.gilmok.api.ai.entity.RequestLog;
import kr.gilmok.api.ai.repository.RequestLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/admin/logs")
@RequiredArgsConstructor
public class AdminLogController {

    private final RequestLogRepository requestLogRepository;

    @GetMapping
    public ResponseEntity<List<RequestLog>> getRecentLogs() {
        return ResponseEntity.ok(requestLogRepository.findTop100ByOrderByTimestampDesc());
    }
}