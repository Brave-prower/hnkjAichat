package com.hnkj.qa.controller;

import com.hnkj.qa.common.api.ApiResponse;
import com.hnkj.qa.dto.request.AskRequest;
import com.hnkj.qa.dto.response.AskResponse;
import com.hnkj.qa.service.QaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * 智能问答接口。
 */
@Tag(name = "智能问答", description = "基于规章制度的自然语言问答，回答带来源页码")
@RestController
@RequestMapping("/qa")
@RequiredArgsConstructor
public class QaController {

    private final QaService qaService;

    @Operation(summary = "提问（同步返回）")
    @PostMapping("/ask")
    public ApiResponse<AskResponse> ask(@Valid @RequestBody AskRequest request) {
        return ApiResponse.ok(qaService.ask(request));
    }

    @Operation(summary = "提问（SSE 流式返回）")
    @PostMapping(value = "/ask/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> askStream(@Valid @RequestBody AskRequest request) {
        return qaService.askStream(request);
    }

    @Operation(summary = "查询会话历史")
    @GetMapping("/history")
    public ApiResponse<List<AskResponse>> history(@RequestParam String sessionId) {
        return ApiResponse.ok(qaService.history(sessionId));
    }
}
