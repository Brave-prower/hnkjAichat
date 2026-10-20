package com.hnkj.qa.repository;

import com.hnkj.qa.entity.VectorChunk;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

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

    /**
     * 查询某文档的全部文本块，按页码、块内顺序排列。
     *
     * <p>只查需要的列（不含 embedding）：一个文档可能有上百块、每块向量
     * 上千维，SELECT * 会把几 MB 的向量数组一起拉回内存，纯属浪费。</p>
     *
     * <p>排序键 pad 用于解决 SQLite/PG 中字符串排序问题：这里统一转整数，
     * page_number 缺失时排到最后。chunk_index 是切分时写入的顺序号。</p>
     */
    @Query(value = """
            SELECT c.id AS id,
                   c.content AS content,
                   (c.metadata ->> 'page_number') AS pageNumber,
                   (c.metadata ->> 'chunk_index') AS chunkIndex,
                   (c.embedding IS NOT NULL) AS hasEmbedding
            FROM document_chunks c
            WHERE c.metadata ->> 'documentId' = :documentId
            ORDER BY
              CASE WHEN (c.metadata ->> 'page_number') ~ '^[0-9]+$'
                   THEN (c.metadata ->> 'page_number')::int ELSE 2147483647 END,
              CASE WHEN (c.metadata ->> 'chunk_index') ~ '^[0-9]+$'
                   THEN (c.metadata ->> 'chunk_index')::int ELSE 2147483647 END,
              c.id
            """, nativeQuery = true)
    List<ChunkRow> findChunksByDocumentId(@Param("documentId") String documentId);

    /**
     * 原生查询的投影接口（Spring Data 会把列别名映射到 getter）。
     */
    interface ChunkRow {
        String getId();

        String getContent();

        String getPageNumber();

        String getChunkIndex();

        Boolean getHasEmbedding();
    }
}
