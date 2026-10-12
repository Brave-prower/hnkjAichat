package com.hnkj.qa.service;

import com.hnkj.qa.dto.request.AskRequest;
import com.hnkj.qa.dto.response.AskResponse;
import reactor.core.publisher.Flux;

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
     * 流式问答（SSE）：逐字返回模型输出。
     */
    Flux<String> askStream(AskRequest request);

    /**
     * 查询某个会话的历史问答。
     */
    List<AskResponse> history(String sessionId);
}
