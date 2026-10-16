package com.hnkj.qa.common.util;

/**
 * 余弦相似度计算工具。
 *
 * <p>余弦相似度用于衡量两个非零向量在向量空间中的方向一致性，
 * 取值范围 {@code [-1, 1]}，1 表示方向完全相同，0 表示正交，-1 表示方向相反。
 * 在文本 Embedding 检索场景下，文本语义越相似，对应向量的余弦相似度越接近 1。</p>
 *
 * <p><b>公式</b>：{@code cos(θ) = (A · B) / (||A|| × ||B||)}</p>
 *
 * <p><b>性能说明</b>：单次调用是 O(n) 时间复杂度（n = 向量维度）。
 * 对 1536 维（如 OpenAI text-embedding-3-small）单次约 1µs，
 * 适合数百~数千个文本块的小型知识库全量扫描场景。</p>
 *
 * <p><b>使用示例</b>：
 * <pre>{@code
 * float[] query = embeddingModel.embed("补考怎么申请？");
 * List<VectorChunk> chunks = repository.findAll();
 * chunks.stream()
 *       .map(c -> new Scored(c, CosineSimilarity.between(query, c.getEmbedding())))
 *       .sorted((a, b) -> Double.compare(b.score, a.score))
 *       .limit(3)
 *       .toList();
 * }</pre>
 * </p>
 */
public final class CosineSimilarity {

    private CosineSimilarity() {
        // 工具类不允许实例化
    }

    /**
     * 计算两个向量的余弦相似度。
     *
     * <p><b>边界处理</b>：
     * <ul>
     *   <li>任一参数为 {@code null}，或两者长度不一致 —— 抛 {@link IllegalArgumentException}</li>
     *   <li>任一为零向量（所有元素为 0）—— 返回 {@code 0.0}，避免除以 0</li>
     * </ul>
     * </p>
     *
     * @param a 向量 A，不可为空
     * @param b 向量 B，不可为空，长度必须与 A 相同
     * @return 余弦相似度，范围 {@code [-1, 1]}；零向量时为 {@code 0.0}
     * @throws IllegalArgumentException 参数非法（null 或长度不一致）
     */
    public static double between(float[] a, float[] b) {
        if (a == null || b == null) {
            throw new IllegalArgumentException("向量不能为 null");
        }
        if (a.length != b.length) {
            throw new IllegalArgumentException(
                    "向量维度不一致：" + a.length + " vs " + b.length);
        }

        double dot = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        for (int i = 0; i < a.length; i++) {
            dot  += (double) a[i] * b[i];
            normA += (double) a[i] * a[i];
            normB += (double) b[i] * b[i];
        }
        if (normA == 0.0 || normB == 0.0) {
            return 0.0;
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}
