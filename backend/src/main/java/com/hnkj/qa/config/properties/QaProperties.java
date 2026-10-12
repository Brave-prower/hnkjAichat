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

    /** 允许上传的文件扩展名 */
    private String allowedExtensions = "pdf";
}
