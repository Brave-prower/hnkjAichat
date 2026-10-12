package com.hnkj.qa.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 提问响应。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "提问响应")
public class AskResponse {

    @Schema(description = "会话标识")
    private String sessionId;

    @Schema(description = "回答内容")
    private String answer;

    @Schema(description = "引用的制度来源列表")
    private List<SourceRef> sources;

    @Schema(description = "本次回答耗时（毫秒）")
    private Integer latencyMs;
}
