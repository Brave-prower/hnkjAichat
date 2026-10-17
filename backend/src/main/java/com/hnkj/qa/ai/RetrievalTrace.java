package com.hnkj.qa.ai;

import lombok.Builder;

import java.util.List;

/**
 * 一次问答的检索过程快照（「检索透明化面板」数据源）。
 *
 * <p>把原本发生在检索层内部的打分、过滤、闸门判断全部显式记录下来，
 * 前端可据此渲染「为什么这样答」面板：哪些候选参与打分、各自得分多少、
 * 谁过了阈值、闸门为什么放行或拒答。</p>
 *
 * <p><b>mode 取值</b>：</p>
 * <ul>
 *   <li>{@code semantic} —— 问题成功向量化，存在语义（余弦）命中</li>
 *   <li>{@code keyword} —— 仅有关键词路命中（存在未向量化文本块）</li>
 *   <li>{@code keyword-fallback} —— 问题无法向量化（Embedding 不可用/熔断），全库走关键词</li>
 * </ul>
 */
@Builder
public record RetrievalTrace(
        /** 检索模式：semantic / keyword / keyword-fallback */
        String mode,
        /** 本次生效的相似度阈值（campus.qa.similarity-threshold） */
        double threshold,
        /** 本次生效的 Top-K（campus.qa.top-k） */
        int topK,
        /** 检索耗时（毫秒） */
        long costMs,
        /** Embedding 熔断器是否处于打开状态（true 时本次必然走关键词） */
        boolean embeddingBroken,
        /** 闸门结论：PASS / REJECT_ALL_BELOW_THRESHOLD */
        String gate,
        /** 闸门结论的人话解释 */
        String gateReason,
        /** 候选明细（含未过阈值的，按分数倒序；最多保留 {@link #MAX_CANDIDATES} 条） */
        List<TraceItem> candidates) {

    public static final String GATE_PASS = "PASS";
    public static final String GATE_REJECT_ALL_BELOW_THRESHOLD = "REJECT_ALL_BELOW_THRESHOLD";

    /** 防止大知识库时 trace 过长：最多记录的候选条数 */
    public static final int MAX_CANDIDATES = 20;

    /**
     * 单条候选的打分明细。
     *
     * @param hit 是否「实际命中」——同时满足：过相似度阈值 且 排名在 Top-K 内
     */
    @Builder
    public record TraceItem(
            String chunkId,
            Long documentId,
            String title,
            Integer pageNumber,
            /** 余弦相似度（保留 4 位小数） */
            double score,
            boolean hit,
            /** 片段内容摘要（前 80 字） */
            String snippet) {
    }
}
