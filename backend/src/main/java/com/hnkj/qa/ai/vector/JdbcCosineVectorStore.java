package com.hnkj.qa.ai.vector;

import com.hnkj.qa.common.util.CosineSimilarity;
import com.hnkj.qa.entity.VectorChunk;
import com.hnkj.qa.repository.VectorChunkRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 基于普通 PostgreSQL 表 + Java 余弦相似度的向量库实现（不依赖 pgvector）。
 *
 * <p><b>设计动机</b>：Windows 本机装不上 pgvector 扩展，开发环境改用普通表
 * {@code document_chunks}（embedding 以 JSONB 数组保存），检索时把向量加载到
 * Java 侧计算余弦相似度，取最相似的 Top-K 个文本块。</p>
 *
 * <p><b>关键词兜底</b>：Embedding 服务未配置或临时不可用时，本实现会
 * <i>自动降级</i>而不是让整条链路失败 ——</p>
 * <ul>
 *   <li>写入：文本块照常入库，只是 {@code embedding} 留空；</li>
 *   <li>检索：问题无法向量化时改为中文 2-gram 关键词匹配。</li>
 * </ul>
 * <p>这样即使没有任何 Embedding API Key，上传的 PDF 依旧能被问答检索到；
 * 等配置好 Key 后调用「重建索引」即可补回语义检索能力。</p>
 *
 * <p><b>性能边界</b>：全量加载 + 内存计算，适合数百~数千个文本块的校园问答场景；
 * 若数据量显著增长，再切换回 pgvector（railway profile）或引入缓存/索引。</p>
 *
 * <p><b>与其他实现的关系</b>：本类仅注册为 local profile 的 Bean；
 * railway profile 使用 Spring AI 自动装配的 {@code PgVectorStore}，
 * 对业务代码（PdfIngestService / RagQaService）完全透明。</p>
 */
@Slf4j
public class JdbcCosineVectorStore implements VectorStore {

    private final VectorChunkRepository repository;
    private final EmbeddingModel embeddingModel;

    /**
     * 启动时即判定 Embedding 是否可用。
     *
     * <p>Key 是占位符（{@code demo-key} / {@code sk-PUT-...}）时直接禁用向量化：
     * 否则每次提问都会先向默认 base-url（api.openai.com，本网络不可达）发起请求，
     * 挂到 TCP 超时才降级，白白多等几十秒。</p>
     */
    private final boolean embeddingDisabledAtStartup;

    public JdbcCosineVectorStore(VectorChunkRepository repository,
                                 EmbeddingModel embeddingModel,
                                 @Value("${spring.ai.openai.api-key:}") String embeddingApiKey) {
        this.repository = repository;
        this.embeddingModel = embeddingModel;
        this.embeddingDisabledAtStartup = isPlaceholderKey(embeddingApiKey);
        if (this.embeddingDisabledAtStartup) {
            log.warn("Embedding API Key 未配置（占位符）→ 已直接禁用向量化，检索走中文关键词匹配；"
                    + "配置 OPENAI_API_KEY / OPENAI_BASE_URL 后重启即可启用语义检索");
        }
    }

    /** 判定是否为未配置的占位 Key */
    private static boolean isPlaceholderKey(String key) {
        if (key == null || key.isBlank()) {
            return true;
        }
        String k = key.trim();
        return "demo-key".equalsIgnoreCase(k) || k.contains("PUT-YOUR");
    }

    /** 检索结果中携带相似度得分的元数据键 */
    public static final String META_SIMILARITY = "similarity";
    /** 检索结果中标记本次召回方式（semantic / keyword） */
    public static final String META_RETRIEVAL_MODE = "retrievalMode";

    /**
     * 关键词兜底模式的最低命中率：
     * 问题的 2-gram 被文本块覆盖的比例低于该值即视为不相关。
     * 取值偏宽松，宁可多召回一些也不漏掉。
     */
    private static final double KEYWORD_MIN_SCORE = 0.15;

    // ==================== Embedding 熔断器 ====================

    /** 连续失败计数；达到阈值即熔断，期间不再请求 Embedding 服务 */
    private final AtomicInteger embedConsecutiveFailures = new AtomicInteger();

    /** 熔断到期时间戳（epoch ms）；熔断期间检索直接走关键词 */
    private volatile long embedBreakerOpenUntil = 0L;

    /** 失败 1 次即熔断：一次失败往往意味着要白等一次网络超时，没必要再试 */
    private static final int EMBED_BREAKER_THRESHOLD = 1;

    /** 熔断持续时长：期间提问不再等 Embedding 重试超时 */
    private static final Duration EMBED_COOLDOWN = Duration.ofMinutes(10);

    // ==================== 写入 ====================

    @Override
    public void add(List<Document> documents) {
        if (documents == null || documents.isEmpty()) {
            return;
        }
        List<String> texts = documents.stream().map(Document::getText).toList();

        // 尝试向量化；失败则降级为「只存内容、不存向量」，
        // 检索阶段走关键词匹配，保证上传的内容仍可被问答读取。
        List<float[]> embeddings = tryEmbed(texts);

        List<VectorChunk> chunks = new ArrayList<>(documents.size());
        for (int i = 0; i < documents.size(); i++) {
            Document doc = documents.get(i);
            chunks.add(VectorChunk.builder()
                    .id(doc.getId())
                    .content(doc.getText())
                    .metadata(doc.getMetadata() == null ? Map.of() : doc.getMetadata())
                    .embedding(embeddings == null ? null : embeddings.get(i))
                    .build());
        }
        repository.saveAll(chunks);
        log.info("向量入库：{} 个文本块（{}）", chunks.size(),
                embeddings == null ? "无向量 · 关键词检索模式" : "余弦语义检索");
    }

    /**
     * 批量向量化。任一步骤失败都返回 {@code null}（表示本次跳过向量化），
     * 而不是抛异常中断入库 —— 文本内容本身比向量更重要。
     */
    private List<float[]> tryEmbed(List<String> texts) {
        if (texts.isEmpty() || embedBroken()) {
            return null;
        }
        try {
            List<float[]> vectors = embeddingModel.embed(texts);
            if (vectors == null || vectors.size() != texts.size()) {
                log.warn("Embedding 返回数量与文本数不一致（{} vs {}），本次跳过向量化",
                        vectors == null ? "null" : vectors.size(), texts.size());
                return null;
            }
            embedConsecutiveFailures.set(0);
            return vectors;
        } catch (Exception e) {
            recordEmbedFailure("Embedding 服务不可用，文本块仅保存内容（检索将使用关键词匹配）：{}", e.getMessage());
            return null;
        }
    }

    /** 熔断是否生效中 */
    private boolean embedBroken() {
        return embeddingDisabledAtStartup
                || System.currentTimeMillis() < embedBreakerOpenUntil;
    }

    /**
     * 记一次 Embedding 失败；连续达到阈值则打开熔断。
     *
     * <p>没有熔断时，Embedding 服务不可用 + Spring AI 默认重试策略
     * （10 次、指数退避）会让每次提问都白等几十秒才落入关键词检索。</p>
     */
    private void recordEmbedFailure(String template, String message) {
        int fails = embedConsecutiveFailures.incrementAndGet();
        if (fails >= EMBED_BREAKER_THRESHOLD) {
            embedBreakerOpenUntil = System.currentTimeMillis() + EMBED_COOLDOWN.toMillis();
            log.warn("Embedding 请求失败 → 熔断 {} 分钟，期间检索直接走关键词（配好 Embedding Key 后重启即可恢复语义检索）",
                    EMBED_COOLDOWN.toMinutes());
        } else {
            log.warn(template, message);
        }
    }

    // ==================== 删除 ====================

    @Override
    public void delete(List<String> idList) {
        if (idList == null || idList.isEmpty()) {
            return;
        }
        repository.deleteAllById(idList);
        log.info("向量删除：{} 条（按 ID）", idList.size());
    }

    /**
     * 按过滤表达式删除，当前仅支持 {@code documentId == 'xxx'} 等值匹配
     * （业务侧唯一使用的删除场景，见 PdfIngestService#deleteByDocumentId）。
     */
    @Override
    public void delete(Filter.Expression expression) {
        String documentId = extractDocumentIdEq(expression);
        if (documentId != null) {
            repository.deleteByDocumentId(documentId);
            log.info("向量删除：文档 [{}] 的全部文本块", documentId);
        } else {
            throw new UnsupportedOperationException(
                    "本向量库仅支持按 documentId 等值删除，收到：" + expression);
        }
    }

    // ==================== 检索 ====================

    /**
     * 检索策略：已向量化的文本块走余弦语义检索，未向量化的文本块走关键词匹配，
     * 两路结果合并后按得分<b>倒序返回全部候选</b>。
     *
     * <p><b>职责变化（透明化）</b>：相似度阈值与 Top-K 的过滤职责已上移到调用方
     * （{@code RagQaService}），本方法只负责打分与排序，便于上层构建包含
     * 「未过阈值候选」的检索快照 {@link com.hnkj.qa.ai.RetrievalTrace}。
     * 关键词路保留内部相关性下限 {@link #KEYWORD_MIN_SCORE}（它过滤的是
     * “毫无重叠”的噪声，而非业务阈值）。</p>
     *
     * <p>注意：railway profile（PgVectorStore）仍会在存储层过滤阈值，
     * 届时候选集只含已过阈值的部分，透明化面板的候选列表会变短但功能不受影响。</p>
     */
    @Override
    public List<Document> similaritySearch(SearchRequest request) {
        long start = System.currentTimeMillis();
        List<VectorChunk> all = repository.findAll();
        if (all.isEmpty()) {
            return List.of();
        }

        List<VectorChunk> vectorized = all.stream().filter(this::hasVector).toList();
        List<VectorChunk> plain = all.stream().filter(c -> !hasVector(c)).toList();

        float[] queryVector = tryEmbedOne(request.getQuery());
        List<Scored> scored = new ArrayList<>();

        // ---------- 第一路：语义检索（余弦）——全量打分，不筛阈值 ----------
        if (queryVector != null && !vectorized.isEmpty()) {
            for (VectorChunk chunk : vectorized) {
                double score = CosineSimilarity.between(queryVector, chunk.getEmbedding());
                scored.add(new Scored(chunk, score, "semantic"));
            }
        }

        // ---------- 第二路：关键词兜底 ----------
        // 问题本身无法向量化（Embedding 整体不可用）→ 全库关键词检索；
        // 否则只补上「尚未向量化」的文本块，避免与语义路重复。
        List<VectorChunk> keywordTargets = (queryVector == null) ? all : plain;
        if (!keywordTargets.isEmpty()) {
            scored.addAll(keywordMatch(request.getQuery(), keywordTargets));
        }

        List<Document> result = scored.stream()
                .sorted(Comparator.comparingDouble(Scored::score).reversed())
                .map(this::toDocument)
                .toList();

        log.debug("检索完成：问题向量维度={}，库内 {} 块（已向量化 {} / 未向量化 {}），候选 {} 条，耗时 {}ms",
                queryVector == null ? 0 : queryVector.length,
                all.size(), vectorized.size(), plain.size(), result.size(),
                System.currentTimeMillis() - start);
        return result;
    }

    /**
     * Embedding 熔断器是否处于打开状态。
     * <p>供 {@code RagQaService} 构建 {@link com.hnkj.qa.ai.RetrievalTrace} 时透出。</p>
     */
    public boolean isEmbeddingBroken() {
        return embedBroken();
    }

    private boolean hasVector(VectorChunk chunk) {
        return chunk.getEmbedding() != null && chunk.getEmbedding().length > 0;
    }

    /** 单条文本向量化；失败返回 null，由调用方决定是否降级 */
    private float[] tryEmbedOne(String text) {
        if (embedBroken()) {
            return null;
        }
        try {
            float[] vector = embeddingModel.embed(text);
            embedConsecutiveFailures.set(0);
            return (vector == null || vector.length == 0) ? null : vector;
        } catch (Exception e) {
            recordEmbedFailure("问题向量化失败，本次使用关键词检索：{}", e.getMessage());
            return null;
        }
    }

    /** 打包成 Spring AI Document，并把得分与召回方式写进 metadata */
    private Document toDocument(Scored scored) {
        Map<String, Object> metadata = new HashMap<>(scored.chunk().getMetadata());
        metadata.put(META_SIMILARITY, scored.score());
        metadata.put(META_RETRIEVAL_MODE, scored.mode());
        return new Document(scored.chunk().getId(), scored.chunk().getContent(), metadata);
    }

    /**
     * 关键词召回：以「问题的 2-gram 被文本块覆盖的比例」作为得分。
     *
     * <p>用于 Embedding 不可用时的兜底。精度不如语义检索，
     * 但能保证「传进来的文档一定读得到」。</p>
     */
    private List<Scored> keywordMatch(String query, List<VectorChunk> chunks) {
        Set<String> queryGrams = bigrams(query);
        if (queryGrams.isEmpty()) {
            return List.of();
        }
        List<Scored> out = new ArrayList<>();
        for (VectorChunk chunk : chunks) {
            Set<String> chunkGrams = bigrams(chunk.getContent());
            if (chunkGrams.isEmpty()) {
                continue;
            }
            int hit = 0;
            for (String gram : queryGrams) {
                if (chunkGrams.contains(gram)) {
                    hit++;
                }
            }
            double score = (double) hit / queryGrams.size();
            if (score >= KEYWORD_MIN_SCORE) {
                out.add(new Scored(chunk, score, "keyword"));
            }
        }
        return out;
    }

    /**
     * 归一化后切 2-gram：只保留中英文与数字字符，忽略空白和标点。
     *
     * <p>中文场景下不引入分词库也能得到可用的匹配粒度，例如
     * 「国家奖学金」→ {国家, 家奖, 奖学, 学金}。</p>
     */
    private static Set<String> bigrams(String text) {
        if (text == null || text.isBlank()) {
            return Set.of();
        }
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (Character.isLetterOrDigit(ch)) {
                sb.append(Character.toLowerCase(ch));
            }
        }
        String s = sb.toString();
        if (s.isEmpty()) {
            return Set.of();
        }
        if (s.length() == 1) {
            return Set.of(s);
        }
        Set<String> grams = new LinkedHashSet<>();
        for (int i = 0; i + 2 <= s.length(); i++) {
            grams.add(s.substring(i, i + 2));
        }
        return grams;
    }

    // ==================== 数学与工具 ====================

    /**
     * 从过滤表达式 AST 中提取 {@code documentId == value} 的等值约束。
     * 仅解析最外层（或 AND 链叶子节点上的）EQ(documentId, ...)，其余返回 null。
     */
    private String extractDocumentIdEq(Filter.Expression expression) {
        if (expression == null) {
            return null;
        }
        if (expression.type() == Filter.ExpressionType.EQ
                && expression.left() instanceof Filter.Key key
                && expression.right() instanceof Filter.Value value) {
            return key.key().equals("documentId") ? String.valueOf(value.value()) : null;
        }
        // AND 链：左右两侧递归找 EQ
        if (expression.type() == Filter.ExpressionType.AND) {
            if (expression.left() instanceof Filter.Expression leftExpr) {
                String left = extractDocumentIdEq(leftExpr);
                if (left != null) {
                    return left;
                }
            }
            if (expression.right() instanceof Filter.Expression rightExpr) {
                return extractDocumentIdEq(rightExpr);
            }
        }
        return null;
    }

    /** 相似度得分 + 原始记录 + 本次召回方式（semantic / keyword） */
    private record Scored(VectorChunk chunk, double score, String mode) {
    }
}
