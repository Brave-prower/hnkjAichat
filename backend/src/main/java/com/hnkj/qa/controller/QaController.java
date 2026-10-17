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
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

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

    /**
     * SSE 流式问答。
     *
     * <p>事件协议：{@code trace → sources → message* → done}（trace 可选，检索失败时省略；
     * done 携带 {@code {"recordId": n}} JSON，为问答记录主键，供反馈评价使用）。</p>
     *
     * <p>这里刻意不加 {@code @Valid}：参数校验失败时全局异常处理器会写 JSON 错误体，
     * 而客户端声明的是 {@code Accept: text/event-stream}，两者冲突会导致 406。
     * 校验改在 {@link QaService#askStream} 内部完成，错误通过 {@code error} 事件回传。</p>
     */
    @Operation(summary = "提问（SSE 流式返回）",
            description = "事件协议 trace → sources → message* → done；"
                    + "trace 为检索过程快照（候选打分/闸门结论）；done 携带问答记录主键 recordId")
    @PostMapping(value = "/ask/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter askStream(@RequestBody(required = false) AskRequest request) {
        return qaService.askStream(request);
    }

    @Operation(summary = "查询会话历史")
    @GetMapping("/history")
    public ApiResponse<List<AskResponse>> history(@RequestParam String sessionId) {
        return ApiResponse.ok(qaService.history(sessionId));
    }
}
