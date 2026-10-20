package com.hnkj.qa.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 文档文本块（供「查看」面板展示切分结果）。
 *
 * <p>用于让使用者直观核对：这篇 PDF 被切成了几块、每块属于第几页、
 * 是否已生成向量。是「检索透明化」在入库侧的对应展示。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "文档文本块")
public class ChunkVO {

    @Schema(description = "文本块 ID")
    private String id;

    @Schema(description = "所属页码（从 1 开始，未知时为 null）")
    private Integer pageNumber;

    @Schema(description = "文本块序号（从 1 开始，仅用于展示排序）")
    private Integer index;

    @Schema(description = "文本内容")
    private String content;

    @Schema(description = "字数")
    private Integer charCount;

    @Schema(description = "是否已生成向量：true 可语义检索 / false 仅关键词可命中")
    private Boolean hasEmbedding;
}
