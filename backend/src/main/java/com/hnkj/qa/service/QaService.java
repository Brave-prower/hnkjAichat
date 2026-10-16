package com.hnkj.qa.service;

import com.hnkj.qa.dto.request.AskRequest;
import com.hnkj.qa.dto.response.AskResponse;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

/**
 * 智能问答服务。
 */
public interface QaService {

    /**
     * 同步问答：检索 → 生成 → 返回答案与来源页码。
     */
    AskResponse ask(AskRequest request);

    /**
     * 流式问答（SSE）。
     *
     * <p>事件序列：{@code sources}（引用来源 JSON）→ 若干 {@code message}（增量正文）
     * → {@code done}。所有失败都以 {@code error} 事件回传、不再向上抛异常——
     * 否则 Spring 会试图按 {@code Accept: text/event-stream} 写出 JSON 错误体而报 406。</p>
     */
    SseEmitter askStream(AskRequest request);

    /**
     * 查询某个会话的历史问答。
     */
    List<AskResponse> history(String sessionId);
}
