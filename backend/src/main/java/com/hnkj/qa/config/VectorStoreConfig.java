package com.hnkj.qa.config;

import com.hnkj.qa.ai.vector.JdbcCosineVectorStore;
import com.hnkj.qa.repository.VectorChunkRepository;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * 向量库按环境切换：
 *
 * <ul>
 *   <li><b>local</b>：普通 PostgreSQL 表 + Java 余弦相似度
 *       （{@link JdbcCosineVectorStore}），本机无需 pgvector 扩展；
 *       pgvector 自动装配已在 application-local.yml 中排除。</li>
 *   <li><b>railway</b>：Spring AI 自动装配的 PgVectorStore
 *       （vector_store 表 + HNSW 索引），需要数据库启用 pgvector 扩展。</li>
 * </ul>
 *
 * <p>两个实现都实现 {@link VectorStore} 接口，
 * PdfIngestService / RagQaService 等业务代码无需感知差异。</p>
 */
@Configuration
public class VectorStoreConfig {

    @Bean
    @Profile("local")
    public VectorStore jdbcCosineVectorStore(VectorChunkRepository repository,
                                              EmbeddingModel embeddingModel,
                                              @Value("${spring.ai.openai.api-key:}") String embeddingApiKey) {
        return new JdbcCosineVectorStore(repository, embeddingModel, embeddingApiKey);
    }
}
