package com.hnkj.qa.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 提问请求。
 */
@Data
@Schema(description = "提问请求")
public class AskRequest {

    @Schema(description = "学生的问题", example = "请销假需要哪些手续？", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "提问内容不能为空")
    @Size(max = 500, message = "问题长度不能超过 500 字")
    private String question;

    @Schema(description = "会话标识，为空则由服务端生成")
    @Size(max = 64, message = "会话标识过长")
    private String sessionId;

    @Schema(description = "是否流式返回（SSE）", defaultValue = "false")
    private Boolean stream = false;
}
