package com.hnkj.qa.dto.response;

import com.hnkj.qa.ai.RetrievalTrace;
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

    @Schema(description = "检索过程快照（透明化面板数据源）：检索模式、候选打分、闸门结论等")
    private RetrievalTrace trace;

    @Schema(description = "问答记录主键（落库后生成），用于提交回答反馈评价")
    private Long recordId;
}
