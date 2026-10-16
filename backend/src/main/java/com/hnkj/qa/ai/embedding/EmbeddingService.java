package com.hnkj.qa.ai.embedding;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Embedding 文本向量化服务。
 *
 * <p>对 Spring AI {@link EmbeddingModel} 的轻量包装，把业务侧关心的
 * "字符串 → float[]" 接口扁平化，并把模型维度等元数据集中暴露。</p>
 *
 * <p><b>底层</b>：Spring AI 自动装配的 EmbeddingModel 默认走 OpenAI 协议
 * （text-embedding-3-small，1536 维）。通过设置环境变量
 * {@code OPENAI_BASE_URL}，可指向任意 OpenAI 兼容服务（OpenAI、硅基流动、
 * 智谱、OpenRouter 等）。</p>
 *
 * <p><b>为什么用 OpenAI 兼容端点而不是 DeepSeek</b>：
 * DeepSeek 截至目前未提供独立的 Embedding 接口。RAG 场景下，
 * 推荐用一个同时支持 chat（DeepSeek）和 embedding（兼容服务）的供应商，
 * 或用本地 BGE-M3 模型。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmbeddingService {

    private final EmbeddingModel embeddingModel;

    /**
     * 把单段文本转向量。
     *
     * @param text 待向量化的文本
     * @return 浮点向量（默认 1536 维，与 text-embedding-3-small 一致）
     */
    public float[] embed(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("待向量化的文本不能为空");
        }
        return embeddingModel.embed(text);
    }

    /**
     * 批量向量化（内部走 EmbeddingModel 的批量接口，比逐条调用更高效）。
     *
     * @param texts 文本列表
     * @return 与输入等长的向量列表
     */
    public List<float[]> embedAll(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }
        return embeddingModel.embed(texts);
    }

    /**
     * Embedding 向量维度（如 1536）。用于运行时校验一致性。
     */
    public int dimension() {
        EmbeddingResponse sample = embeddingModel.embedForResponse(List.of("ping"));
        if (sample == null || sample.getResults().isEmpty()) {
            return -1;
        }
        return sample.getResults().get(0).getOutput().length;
    }
}
