package com.hnkj.qa.ai;

import com.hnkj.qa.ai.deepseek.DeepSeekClient;
import com.hnkj.qa.ai.vector.JdbcCosineVectorStore;
import com.hnkj.qa.common.enums.ErrorCode;
import com.hnkj.qa.common.exception.BizException;
import com.hnkj.qa.config.properties.QaProperties;
import com.hnkj.qa.dto.response.SourceRef;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * RAG 问答核心管线：检索 → 拼上下文 → 调 DeepSeek → 解析引用。
 *
 * <p><b>流程</b>：</p>
 * <ol>
 *   <li>用 {@link VectorStore} 做相似度检索，召回 Top-K 个文本块（带 page_number 元数据）</li>
 *   <li>把命中片段格式化为 {@code [第N页] 原文} 列表，填入 system 提示词模板</li>
 *   <li>调用 {@link DeepSeekClient} 生成回答</li>
 *   <li>把命中片段转换为 {@link SourceRef} 列表（去重 + 按相似度排序）一并返回</li>
 * </ol>
 *
 * <p>本类不感知具体向量库实现（local 余弦表 / pgvector 均走同一接口），
 * 也不持久化问答历史（由 QaService 层处理）。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RagQaService {

    private final VectorStore vectorStore;
    private final DeepSeekClient deepSeekClient;
    private final QaProperties qaProperties;

    /** 传给模型的 user 消息（问题正文已在 system 提示词里，这里只做指令） */
    private static final String USER_PROMPT = "请基于上述【参考资料】回答问题。";

    /** 检索无命中时的标准应答 */
    public static final String NO_REFERENCE_REPLY =
            "抱歉，学校现行规章制度中未找到与您问题相关的内容，建议咨询辅导员或学院办公室获取准确答复。";

    /** 系统提示词模板（从 classpath 加载，运行时不可变） */
    private String systemPromptTemplate;

    @PostConstruct
    void loadPrompt() {
        try {
            ClassPathResource res = new ClassPathResource("prompts/qa-system-prompt.st");
            this.systemPromptTemplate = new String(res.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            log.info("RAG 系统提示词已加载（{} 字符）", systemPromptTemplate.length());
        } catch (IOException e) {
            log.warn("加载 prompts/qa-system-prompt.st 失败，使用兜底提示词", e);
            this.systemPromptTemplate = "你是规章制度咨询助手。仅依据参考资料回答。\n【参考资料】\n{context}\n【问题】\n{question}";
        }
    }

    /**
     * 执行一次 RAG 问答。
     *
     * @param question 学生问题（已做非空校验）
     * @return 回答正文 + 引用来源列表
     * @throws BizException 检索或生成失败时抛出
     */
    public RagResult answer(String question) {
        Prepared prepared = prepare(question);

        // ---------- 无命中：返回标准应答 ----------
        if (prepared.hits().isEmpty()) {
            return new RagResult(NO_REFERENCE_REPLY, List.of());
        }

        // ---------- 调 DeepSeek 生成 ----------
        String answer;
        try {
            // system + user 二段式：让 system 提示词完整生效，user 只携带指令
            answer = deepSeekClient.chat(prepared.systemPrompt(), prepared.userPrompt());
        } catch (BizException e) {
            log.error("DeepSeek 生成失败，问题=[{}]", truncate(question, 40), e);
            throw e;
        }

        return new RagResult(answer, prepared.sources());
    }

    /**
     * 检索 + 拼提示词。同步问答与流式问答共用这一前置步骤，
     * 保证两条链路的召回结果与引用来源完全一致。
     *
     * @param question 学生问题
     * @return 命中片段、渲染好的系统提示词、来源列表；无命中时 {@code hits} 与 {@code sources} 均为空
     * @throws BizException 问题为空时抛出
     */
    public Prepared prepare(String question) {
        if (question == null || question.isBlank()) {
            throw new BizException(ErrorCode.QUESTION_EMPTY);
        }

        int topK = qaProperties.getTopK();
        double threshold = qaProperties.getSimilarityThreshold();

        // ---------- 1. 全量打分（存储层已不再过滤，阈值/TopK 职责上移至此） ----------
        long start = System.currentTimeMillis();
        SearchRequest searchRequest = SearchRequest.builder()
                .query(question)
                .topK(topK)
                .similarityThreshold(threshold)
                .build();
        List<Document> allCandidates = vectorStore.similaritySearch(searchRequest);

        // ---------- 1.5 业务阈值过滤 + Top-K 截断（按分数倒序传入） ----------
        List<Document> passed = allCandidates.stream()
                .filter(d -> {
                    Double s = doubleOr(d.getMetadata(), "similarity");
                    return s != null && s >= threshold;
                })
                .toList();
        List<Document> hits = passed.size() > topK ? passed.subList(0, topK) : passed;
        log.info("RAG 检索：问题=[{}]，候选 {} 条，过阈值(≥{}) {} 条，取 Top-{}，命中 {} 条，耗时 {}ms",
                truncate(question, 40), allCandidates.size(), threshold,
                passed.size(), topK, hits.size(), System.currentTimeMillis() - start);

        // ---------- 1.8 检索过程快照（透明化面板数据源） ----------
        RetrievalTrace trace = buildTrace(allCandidates, passed, hits, topK, threshold,
                System.currentTimeMillis() - start);

        if (hits.isEmpty()) {
            return new Prepared(List.of(), null, null, List.of(), trace);
        }

        // ---------- 2. 相关性闸门：最高分过低说明只是关键词弱重叠，宁可不作答 ----------
        Double topScore = topSimilarity(hits);
        if (topScore != null && topScore < qaProperties.getMinAnswerableScore()) {
            log.info("RAG 相关性闸门：最高分 {} 低于阈值 {}，按无命中处理（避免弱相关来源误导用户）",
                    String.format("%.3f", topScore), qaProperties.getMinAnswerableScore());
            return new Prepared(List.of(), null, null, List.of(), trace);
        }

        // ---------- 3. 拼上下文 + 提示词 ----------
        String context = renderContext(hits);
        String systemPrompt = systemPromptTemplate
                .replace("{context}", context)
                .replace("{question}", question);

        // ---------- 4. 构造 SourceRef 列表（按相似度倒序、去重 documentId+page） ----------
        return new Prepared(hits, systemPrompt, USER_PROMPT, toSourceRefs(hits), trace);
    }

    /**
     * 构建检索过程快照。
     *
     * @param hitSet 实际命中（过阈值且在 Top-K 内）的文档集合
     */
    private RetrievalTrace buildTrace(List<Document> allCandidates, List<Document> passed,
                                      List<Document> hits, int topK, double threshold, long costMs) {
        boolean embeddingBroken = vectorStore instanceof JdbcCosineVectorStore store
                && store.isEmbeddingBroken();

        List<RetrievalTrace.TraceItem> items = new ArrayList<>();
        boolean anySemantic = false;
        for (Document d : allCandidates) {
            if (items.size() >= RetrievalTrace.MAX_CANDIDATES) {
                break;
            }
            Map<String, Object> meta = d.getMetadata() == null ? Map.of() : d.getMetadata();
            Double score = doubleOr(meta, "similarity");
            if (score == null) {
                continue;
            }
            String mode = stringOr(meta, JdbcCosineVectorStore.META_RETRIEVAL_MODE, "");
            anySemantic = anySemantic || "semantic".equals(mode);
            boolean hit = hits.stream().anyMatch(h -> h.getId().equals(d.getId()));
            items.add(RetrievalTrace.TraceItem.builder()
                    .chunkId(d.getId())
                    .documentId(longOr(meta, "documentId"))
                    .title(stringOr(meta, "title", "未知文档"))
                    .pageNumber(intOr(meta, "page_number"))
                    .score(Math.round(score * 10000.0) / 10000.0)
                    .hit(hit)
                    .snippet(truncate(d.getText(), 80))
                    .build());
        }

        String mode = anySemantic ? "semantic"
                : (embeddingBroken ? "keyword-fallback" : "keyword");

        String gate;
        String gateReason;
        if (!hits.isEmpty()) {
            gate = RetrievalTrace.GATE_PASS;
            gateReason = "最高分 " + formatScore(topSimilarity(hits))
                    + " ≥ 最低可答分 " + qaProperties.getMinAnswerableScore() + "，放行作答";
        } else {
            gate = RetrievalTrace.GATE_REJECT_ALL_BELOW_THRESHOLD;
            Double best = topSimilarity(allCandidates);
            gateReason = best == null
                    ? "知识库中没有可参与比较的候选"
                    : "最高分 " + formatScore(best) + " 未达到相似度阈值 " + threshold
                            + "，宁可不答也不编造";
        }

        return RetrievalTrace.builder()
                .mode(mode)
                .threshold(threshold)
                .topK(topK)
                .costMs(costMs)
                .embeddingBroken(embeddingBroken)
                .gate(gate)
                .gateReason(gateReason)
                .candidates(items)
                .build();
    }

    private static String formatScore(Double score) {
        return score == null ? "-" : String.format("%.4f", score);
    }

    /**
     * 大模型不可用时的兜底回答：直接把召回的制度原文按页码列出来。
     *
     * <p>这样即使生成模型挂了，用户仍然能拿到"检索 → 出处"这一段真实结果，
     * 而不是一句含糊的"服务不可用"。</p>
     *
     * @param hits 检索命中的片段
     * @param reason 模型不可用的简短原因
     */
    public String fallbackAnswer(List<Document> hits, String reason) {
        StringBuilder sb = new StringBuilder();
        sb.append("⚠️ 大模型生成服务暂时不可用（").append(reason).append("）。\n")
          .append("以下是知识库中与您问题最相关的制度原文，供参考：\n");
        for (int i = 0; i < hits.size(); i++) {
            Document hit = hits.get(i);
            String title = stringOr(hit.getMetadata(), "title", "未知文档");
            Integer page = intOr(hit.getMetadata(), "page_number");
            sb.append("\n【").append(i + 1).append("】").append(title)
              .append(page != null ? "（第 " + page + " 页）" : "")
              .append("\n").append(hit.getText().trim()).append("\n");
        }
        return sb.toString();
    }

    // ============================================================
    // 内部工具
    // ============================================================

    /**
     * 把命中片段渲染为参考资料文本，格式：
     * <pre>
     * [文档标题-1] 第 3 页：
     *   片段内容...
     *
     * [文档标题-1] 第 5 页：
     *   片段内容...
     * </pre>
     */
    private String renderContext(List<Document> hits) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < hits.size(); i++) {
            Document hit = hits.get(i);
            String title = stringOr(hit.getMetadata(), "title", "未知文档");
            Integer page = intOr(hit.getMetadata(), "page_number");
            String pageLabel = page != null ? "第 " + page + " 页" : "页码未知";

            sb.append("【参考资料 ").append(i + 1).append("】")
              .append(title).append(" — ").append(pageLabel).append("\n")
              .append(hit.getText().trim())
              .append("\n\n");
        }
        return sb.toString().trim();
    }

    /**
     * 把 Spring AI Document 列表转为前端友好的 SourceRef 列表。
     * <p>相同 (documentId, page) 只保留第一条（按相似度倒序传入时即分数最高）。</p>
     */
    private List<SourceRef> toSourceRefs(List<Document> hits) {
        List<SourceRef> result = new ArrayList<>();
        for (Document hit : hits) {
            Map<String, Object> meta = hit.getMetadata() == null ? Map.of() : hit.getMetadata();
            Long documentId = longOr(meta, "documentId");
            String title = stringOr(meta, "title", "未知文档");
            Integer page = intOr(meta, "page_number");
            Double score = doubleOr(meta, "similarity");

            result.add(SourceRef.builder()
                    .documentId(documentId)
                    .title(title)
                    .page(page)
                    .snippet(truncate(hit.getText(), 200))
                    .score(score)
                    .build());
        }
        return result;
    }

    private static String stringOr(Map<String, Object> m, String k, String def) {
        Object v = m == null ? null : m.get(k);
        return v == null ? def : String.valueOf(v);
    }

    /**
     * 取命中列表里的最高相似度分（结果按分数倒序传入，故取第一个非空值即可）。
     * 元数据缺失（老数据）时返回 {@code null}，闸门放行。
     */
    private static Double topSimilarity(List<Document> hits) {
        for (Document hit : hits) {
            Object v = hit.getMetadata() == null ? null : hit.getMetadata().get("similarity");
            if (v instanceof Number n) {
                return n.doubleValue();
            }
        }
        return null;
    }

    private static Integer intOr(Map<String, Object> m, String k) {
        Object v = m == null ? null : m.get(k);
        if (v == null) return null;
        if (v instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(String.valueOf(v));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Long longOr(Map<String, Object> m, String k) {
        Object v = m == null ? null : m.get(k);
        if (v == null) return null;
        if (v instanceof Number n) return n.longValue();
        try {
            return Long.parseLong(String.valueOf(v));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Double doubleOr(Map<String, Object> m, String k) {
        Object v = m == null ? null : m.get(k);
        if (v == null) return null;
        if (v instanceof Number n) return n.doubleValue();
        try {
            return Double.parseDouble(String.valueOf(v));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }

    // ============================================================
    // 承载结果
    // ============================================================

    /**
     * RAG 单次问答结果。
     *
     * @param answer  模型回答正文
     * @param sources 引用来源（按相似度倒序，可能为空）
     */
    public record RagResult(String answer, List<SourceRef> sources) {
    }

    /**
     * 检索阶段产物，供"同步生成"与"流式生成"两条链路复用。
     *
     * @param hits         命中的文档片段（过阈值且在 Top-K 内）
     * @param systemPrompt 已填充 {context}/{question} 的系统提示词（无命中时为 null）
     * @param userPrompt   固定的 user 指令（无命中时为 null）
     * @param sources      引用来源列表
     * @param trace        检索过程快照（透明化面板数据源）
     */
    public record Prepared(
            List<Document> hits,
            String systemPrompt,
            String userPrompt,
            List<SourceRef> sources,
            RetrievalTrace trace) {
    }
}
