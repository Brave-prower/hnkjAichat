package com.hnkj.qa.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 引用来源。回答中标注页码的核心数据结构。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "回答引用的制度来源")
public class SourceRef {

    @Schema(description = "文档 ID")
    private Long documentId;

    @Schema(description = "文档标题")
    private String title;

    @Schema(description = "页码（从 1 开始）")
    private Integer page;

    @Schema(description = "命中的原文片段")
    private String snippet;

    @Schema(description = "相似度得分 0~1")
    private Double score;
}
