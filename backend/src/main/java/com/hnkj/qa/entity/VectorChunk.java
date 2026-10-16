package com.hnkj.qa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.Map;

/**
 * 文档文本块 + 向量（本地余弦检索方案，不依赖 pgvector）。
 *
 * <p>仅在 local profile 下由 {@code JdbcCosineVectorStore} 读写；
 * railway（pgvector）profile 走 Spring AI 自动创建的 vector_store 表。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "document_chunks")
public class VectorChunk {

    /** 文本块 ID（Spring AI Document 的 UUID） */
    @Id
    @Column(length = 64)
    private String id;

    /** 文本块内容 */
    @Column(nullable = false, columnDefinition = "text")
    private String content;

    /**
     * 元数据：documentId / page_number / title 等，
     * 以 JSONB 保存，读写为 Map。
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    /**
     * Embedding 向量：以 JSONB 数组保存（如 [0.012, -0.33, ...]），
     * 检索时在 Java 侧计算余弦相似度。
     *
     * <p>允许为 {@code null}：Embedding 服务不可用时文本块仍会入库，
     * 检索阶段退化为关键词匹配（见 V4 迁移）。</p>
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private float[] embedding;
}
