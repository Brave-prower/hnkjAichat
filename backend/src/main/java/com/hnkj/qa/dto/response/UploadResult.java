package com.hnkj.qa.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * PDF 上传结果。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "PDF 上传结果")
public class UploadResult {

    @Schema(description = "原始文件名", example = "学生管理规定.pdf")
    private String fileName;

    @Schema(description = "落盘后的存储文件名（带时间戳前缀）")
    private String storedName;

    @Schema(description = "提取出的文字长度（字符数）", example = "15230")
    private Integer textLength;

    @Schema(description = "PDF 页数", example = "12")
    private Integer pageCount;
}
