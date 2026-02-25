package kr.gilmok.api.ai.controller;

import kr.gilmok.api.ai.entity.RequestLog;
import kr.gilmok.api.ai.repository.RequestLogRepository;
import kr.gilmok.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/logs") // 프론트엔드 URL과 일치시키기 위해 /api 추가
@RequiredArgsConstructor
@CrossOrigin(origins = "*")  // 👉 핵심! 모든 프론트엔드의 접근을 허용합니다!
public class AdminLogController {

    private final RequestLogRepository requestLogRepository;

    @GetMapping
    public ResponseEntity<ApiResponse<List<RequestLog>>> getRecentLogs() {
        // DB에서 데이터 100개를 가져옵니다.
        List<RequestLog> logs = requestLogRepository.findTop100ByOrderByTimestampDesc();

        // ApiResponse.success()로 감싸서 리턴합니다!
        return ResponseEntity.ok(ApiResponse.success(logs));
    }
}