package com.hnkj.qa.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 问答相关业务参数，对应 application.yml 中 campus.qa.*
 */
@Data
@ConfigurationProperties(prefix = "campus.qa")
public class QaProperties {

    /** 检索返回的片段数量 */
    private int topK = 5;

    /** 相似度阈值 */
    private double similarityThreshold = 0.6;

    /**
     * 相关性闸门：最高命中分低于该值时按"未找到"处理。
     *
     * <p>关键词兜底检索对召回相当宽松（如"有啥专业"这种口语问题也能靠
     * "专业"一词召回一堆弱相关片段），直接拿去生成会出现"答案说没找到、
     * 下面却挂着一堆不相关来源"的观感。低于该分数宁可不作答。</p>
     */
    private double minAnswerableScore = 0.35;

    /** 允许上传的文件扩展名 */
    private String allowedExtensions = "pdf";
}
