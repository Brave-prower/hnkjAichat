package com.hnkj.qa.ai;

import com.hnkj.qa.config.properties.QaProperties;
import com.hnkj.qa.dto.response.SourceRef;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * RAG 问答管线：检索 → 组装上下文 → 生成回答 → 解析引用。
 *
 * <p>骨架版本：仅定义流程，具体实现待填充。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RagQaService {

    private final ChatClient chatClient;
    private final VectorStore vectorStore;
    private final QaProperties qaProperties;

    /**
     * 执行一次 RAG 问答。
     *
     * @param question 学生问题
     * @return 回答文本与引用来源
     */
    public RagResult answer(String question) {
        // TODO Step 1：相似度检索 Top-K
        SearchRequest searchRequest = SearchRequest.builder()
                .query(question)
                .topK(qaProperties.getTopK())
                .similarityThreshold(qaProperties.getSimilarityThreshold())
                .build();
        List<Document> hits = vectorStore.similaritySearch(searchRequest);

        // TODO Step 2：把 hits 拼成 context 文本，格式 [第N页] 片段内容
        // TODO Step 3：调用 ChatClient，system = prompts/qa-system-prompt.st
        // TODO Step 4：解析回答中的 [第X页]，与 hits 的 page_number 对齐，构建 SourceRef 列表
        log.info("待实现：收到问题 [{}]，召回 {} 条片段", question, hits.size());
        return new RagResult("RAG 问答功能待实现", List.of());
    }

    /**
     * RAG 结果承载。
     *
     * @param answer  模型回答
     * @param sources 引用来源
     */
    public record RagResult(String answer, List<SourceRef> sources) {
    }
}
