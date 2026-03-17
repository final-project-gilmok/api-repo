package kr.gilmok.api.event.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import kr.gilmok.api.event.dto.EventCreateRequest;
import kr.gilmok.api.event.dto.EventResponse;
import kr.gilmok.api.event.service.EventService;
import kr.gilmok.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/events")
@RequiredArgsConstructor
@Tag(name = "Admin Event", description = "관리자 이벤트 관리 API")
@SecurityRequirement(name = "bearerAuth")
public class EventController {

    private final EventService eventService;

    @GetMapping
    @Operation(summary = "이벤트 목록 조회", description = "관리자 이벤트 목록을 생성일 기준 내림차순으로 조회합니다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공")
    })
    public ApiResponse<List<EventResponse>> list() {
        return ApiResponse.success(eventService.getEvents());
    }

    @PostMapping
    @Operation(summary = "이벤트 생성", description = "새로운 이벤트를 생성하고 기본 정책을 함께 생성합니다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "생성 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "요청 값 검증 실패")
    })
    public ApiResponse<EventResponse> create(@Valid @RequestBody EventCreateRequest request) {
        return ApiResponse.success(eventService.createEvent(request));
    }

    @GetMapping("/{eventId}")
    @Operation(summary = "이벤트 단건 조회", description = "eventId로 이벤트 상세를 조회합니다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "이벤트 없음")
    })
    public ApiResponse<EventResponse> getEvent(@PathVariable Long eventId) {
        return ApiResponse.success(eventService.getEvent(eventId));
    }

    @PostMapping("/{eventId}/open")
    @Operation(summary = "이벤트 오픈", description = "이벤트 상태를 OPEN으로 변경합니다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "오픈 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "이벤트 없음")
    })
    public ApiResponse<EventResponse> open(@PathVariable Long eventId) {
        return ApiResponse.success(eventService.openEvent(eventId));
    }

    @PostMapping("/{eventId}/close")
    @Operation(summary = "이벤트 종료", description = "이벤트 상태를 CLOSED로 변경하고 정책 캐시를 무효화합니다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "종료 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "이벤트 없음")
    })
    public ApiResponse<EventResponse> close(@PathVariable Long eventId) {
        return ApiResponse.success(eventService.closeEvent(eventId));
    }
}
