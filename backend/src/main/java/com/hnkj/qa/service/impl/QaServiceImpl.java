package com.hnkj.qa.service.impl;

import com.hnkj.qa.common.enums.ErrorCode;
import com.hnkj.qa.common.exception.BizException;
import com.hnkj.qa.dto.request.AskRequest;
import com.hnkj.qa.dto.response.AskResponse;
import com.hnkj.qa.service.QaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * 问答服务实现 —— 骨架版本，业务逻辑待填充。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QaServiceImpl implements QaService {

    // TODO 依赖注入：RagQaService、ChatRecordRepository

    @Override
    public AskResponse ask(AskRequest request) {
        // TODO 1. 向量检索 Top-K（带 pageNumber 元数据）
        // TODO 2. 组装上下文 + 系统提示词（prompts/qa-system-prompt.st）
        // TODO 3. 调用 ChatClient 生成回答
        // TODO 4. 解析回答中的 [第X页] 标记，构建 sources 列表
        // TODO 5. 持久化 ChatRecord
        throw new BizException(ErrorCode.AI_SERVICE_ERROR, "问答功能待实现");
    }

    @Override
    public Flux<String> askStream(AskRequest request) {
        // TODO 流式输出，先推 sources 事件再逐字推送答案
        return Flux.error(new BizException(ErrorCode.AI_SERVICE_ERROR, "流式问答待实现"));
    }

    @Override
    public List<AskResponse> history(String sessionId) {
        // TODO 按 sessionId 查询历史记录
        throw new BizException(ErrorCode.SYSTEM_ERROR, "历史记录查询待实现");
    }
}
