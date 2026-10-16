package com.hnkj.qa.repository;

import com.hnkj.qa.entity.VectorChunk;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/**
 * 本地向量表访问层。
 *
 * <p>检索采用全量加载 + Java 余弦相似度（文档量小，足够用），
 * 若未来数据量大再引入 SQL/向量索引优化。</p>
 */
public interface VectorChunkRepository extends JpaRepository<VectorChunk, String> {

    /**
     * 按文档 ID 删除其全部向量（metadata.documentId 为字符串）。
     */
    @Modifying
    @Transactional
    @Query(value = "DELETE FROM document_chunks WHERE metadata ->> 'documentId' = :documentId",
            nativeQuery = true)
    void deleteByDocumentId(@Param("documentId") String documentId);
}
