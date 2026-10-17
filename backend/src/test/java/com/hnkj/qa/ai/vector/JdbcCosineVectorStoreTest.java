package com.hnkj.qa.ai.vector;

import com.hnkj.qa.common.util.CosineSimilarity;
import com.hnkj.qa.entity.VectorChunk;
import com.hnkj.qa.repository.VectorChunkRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.filter.Filter;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * JdbcCosineVectorStore 单元测试：纯 JUnit + Mockito，不依赖数据库与 Spring 容器。
 */
@ExtendWith(MockitoExtension.class)
class JdbcCosineVectorStoreTest {

    @Mock
    private VectorChunkRepository repository;

    @Mock
    private EmbeddingModel embeddingModel;

    @Captor
    private ArgumentCaptor<List<VectorChunk>> chunksCaptor;

    private JdbcCosineVectorStore store;

    @BeforeEach
    void setUp() {
        // 第三个参数是 Embedding API Key：传非占位值，保证测试中向量化路径处于启用状态
        store = new JdbcCosineVectorStore(repository, embeddingModel, "sk-test-key");
    }

    // ==================== 余弦数学 ====================

    @Test
    @DisplayName("余弦相似度：同向向量 = 1，正交向量 = 0，反向向量 = -1")
    void cosineBasics() {
        float[] a = {1f, 0f};
        assertThat(CosineSimilarity.between(a, a)).isCloseTo(1.0, within(1e-9));
        assertThat(CosineSimilarity.between(a, new float[]{0f, 1f})).isCloseTo(0.0, within(1e-9));
        assertThat(CosineSimilarity.between(a, new float[]{-1f, 0f})).isCloseTo(-1.0, within(1e-9));
        // 零向量无方向，返回 0
        assertThat(CosineSimilarity.between(new float[]{0f, 0f}, a)).isCloseTo(0.0, within(1e-9));
        // 缩放不变性
        assertThat(CosineSimilarity.between(a, new float[]{3f, 0f})).isCloseTo(1.0, within(1e-9));
    }

    // ==================== 写入 ====================

    @Test
    @DisplayName("add：向量化并落库，元数据完整保留")
    void addPersistsChunksWithMetadata() {
        List<Document> docs = List.of(
                Document.builder().id("id-1").text("第一章").metadata(Map.of("page_number", 1)).build(),
                Document.builder().id("id-2").text("第二章")
                        .metadata(Map.of("page_number", 2, "documentId", "9")).build());
        when(embeddingModel.embed(anyList()))
                .thenReturn(List.of(new float[]{1f, 0f}, new float[]{0f, 1f}));

        store.add(docs);

        verify(repository).saveAll(chunksCaptor.capture());
        List<VectorChunk> saved = chunksCaptor.getValue();
        assertThat(saved).hasSize(2);
        assertThat(saved.get(0).getId()).isEqualTo("id-1");
        assertThat(saved.get(0).getContent()).isEqualTo("第一章");
        assertThat(saved.get(0).getMetadata()).containsEntry("page_number", 1);
        assertThat(saved.get(0).getEmbedding()).containsExactly(1f, 0f);
        assertThat(saved.get(1).getMetadata()).containsEntry("documentId", "9");
    }

    @Test
    @DisplayName("add：空列表直接返回，不调用模型")
    void addEmptyListIsNoop() {
        store.add(List.of());
        verify(embeddingModel, never()).embed(anyList());
    }

    // ==================== 检索 ====================

    @Test
    @DisplayName("相似检索：全量打分按余弦降序返回（阈值与 TopK 过滤上移给调用方）")
    void similaritySearchScoresAllCandidates() {
        // 查询向量 (1, 0)：与各块的相似度分别为 1.0 / 0.8 / ~0.707 / 0.5 / 0.0
        when(embeddingModel.embed("学籍管理")).thenReturn(new float[]{1f, 0f});
        when(repository.findAll()).thenReturn(List.of(
                chunk("c-low", "无关内容", new float[]{0f, 1f}),          // 相似度 0.0 → 也在候选里，hit 判断交给调用方
                chunk("c-3", "第三相似", new float[]{0.7071f, 0.7071f}),  // ~0.707
                chunk("c-1", "最相似", new float[]{1f, 0f}),              // 1.0
                chunk("c-2", "第二相似", new float[]{0.8f, 0.6f}),        // 0.8
                chunk("c-mid", "中等", new float[]{0.5f, 0.866f})));      // 0.5 → 同样返回

        List<Document> candidates = store.similaritySearch(SearchRequest.builder()
                .query("学籍管理")
                .topK(3)
                .similarityThreshold(0.6)
                .build());

        // 全量候选都返回，不再在存储层过滤阈值 / 截断 TopK
        assertThat(candidates).hasSize(5);
        assertThat(candidates).extracting(Document::getText)
                .containsExactly("最相似", "第二相似", "第三相似", "中等", "无关内容");
        // 元数据携带相似度得分，且按得分降序
        double s1 = (double) candidates.get(0).getMetadata().get(JdbcCosineVectorStore.META_SIMILARITY);
        double s2 = (double) candidates.get(1).getMetadata().get(JdbcCosineVectorStore.META_SIMILARITY);
        double s3 = (double) candidates.get(2).getMetadata().get(JdbcCosineVectorStore.META_SIMILARITY);
        assertThat(s1).isGreaterThan(s2).isGreaterThan(s3);
        assertThat(s1).isCloseTo(1.0, within(1e-6));
        // 原始元数据（页码）透传
        assertThat(candidates.get(0).getMetadata()).containsEntry("page_number", 2);
    }

    // ==================== 删除 ====================

    @Test
    @DisplayName("按 documentId 等值过滤表达式删除")
    void deleteByDocumentIdFilter() {
        Filter.Expression filter = new Filter.Expression(
                Filter.ExpressionType.EQ,
                new Filter.Key("documentId"),
                new Filter.Value("5"));

        store.delete(filter);

        verify(repository).deleteByDocumentId("5");
    }

    @Test
    @DisplayName("delete(List)：按 ID 删除")
    void deleteByIds() {
        store.delete(List.of("a", "b"));
        verify(repository).deleteAllById(List.of("a", "b"));
    }

    // ==================== 工具 ====================

    private VectorChunk chunk(String id, String content, float[] embedding) {
        return VectorChunk.builder()
                .id(id)
                .content(content)
                .metadata(Map.of("documentId", "1", "page_number", 2, "title", "学生管理规定"))
                .embedding(embedding)
                .build();
    }
}
