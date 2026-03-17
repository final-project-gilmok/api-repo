package kr.gilmok.api.ai.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import kr.gilmok.api.ai.entity.RequestLog;
import kr.gilmok.api.ai.repository.RequestLogRepository;
import kr.gilmok.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/admin/logs")
@RequiredArgsConstructor
@Tag(name = "Admin Log", description = "관리자 AI 요청 로그 조회 API")
@SecurityRequirement(name = "bearerAuth")
public class AdminLogController {

    private final RequestLogRepository requestLogRepository;

    @GetMapping
    @Operation(summary = "최근 요청 로그 조회", description = "최신 요청 로그 100건을 시간 역순으로 조회합니다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패")
    })
    public ResponseEntity<ApiResponse<List<RequestLog>>> getRecentLogs() {
        // DB에서 데이터 100개를 가져옵니다.
        List<RequestLog> logs = requestLogRepository.findTop100ByOrderByTimestampDesc();

        // ApiResponse.success()로 감싸서 리턴합니다!
        return ResponseEntity.ok(ApiResponse.success(logs));
    }
}