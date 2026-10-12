package com.hnkj.qa.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 文档列表项。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "规章制度文档")
public class DocumentVO {

    @Schema(description = "文档 ID")
    private Long id;

    @Schema(description = "文档标题")
    private String title;

    @Schema(description = "原始文件名")
    private String fileName;

    @Schema(description = "文件大小（字节）")
    private Long fileSize;

    @Schema(description = "页数")
    private Integer pageCount;

    @Schema(description = "处理状态：PENDING/PARSING/READY/FAILED")
    private String status;

    @Schema(description = "在线预览地址")
    private String previewUrl;

    @Schema(description = "上传时间")
    private LocalDateTime createdAt;
}
