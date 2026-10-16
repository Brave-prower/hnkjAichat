package com.hnkj.qa.ai.deepseek;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hnkj.qa.common.enums.ErrorCode;
import com.hnkj.qa.common.exception.BizException;
import com.hnkj.qa.config.properties.DeepSeekProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.function.Consumer;

/**
 * DeepSeek 大模型 HTTP 客户端。
 *
 * <p>基于 Spring 6 {@link RestClient} 直接调用 DeepSeek 的 Chat Completions 接口
 * （OpenAI 兼容协议）。相比走 Spring AI 的 ChatClient，本类：</p>
 * <ul>
 *   <li>直接控制请求体/响应解析，无 Spring AI 提示词模板约束；</li>
 *   <li>把 4xx/5xx 错误翻译为业务异常 {@link BizException}，便于上层统一处理；</li>
 *   <li>单元测试时可注入 Mock RestClient，不依赖 Spring AI 框架。</li>
 * </ul>
 *
 * <p><b>典型用法</b>：</p>
 * <pre>{@code
 * String answer = deepSeekClient.chat(
 *     "你是湖南科技职业学院的规章制度咨询助手。",
 *     "问题：" + question + "\n参考资料：" + context);
 * }</pre>
 */
@Slf4j
public class DeepSeekClient {

    /** 解析流式分片用的独立 ObjectMapper（线程安全，无需依赖 Spring 容器） */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final RestClient restClient;
    private final DeepSeekProperties props;
    private final Duration timeout;

    public DeepSeekClient(RestClient restClient, DeepSeekProperties props, Duration timeout) {
        this.restClient = restClient;
        this.props = props;
        this.timeout = timeout;
    }

    // ============================================================
    // 对外 API
    // ============================================================

    /**
     * 单轮对话：传入 system 提示词 + user 内容，返回模型回答的纯文本。
     *
     * @param systemPrompt 系统提示词（角色、行为约束等）
     * @param userPrompt   用户消息（问题 + 检索出的参考资料）
     * @return 模型生成的回答正文
     * @throws BizException 鉴权失败、配额不足、网络异常或返回结构异常时抛出
     */
    public String chat(String systemPrompt, String userPrompt) {
        List<Message> messages = List.of(
                new Message("system", systemPrompt),
                new Message("user", userPrompt));
        return chat(messages);
    }

    /**
     * 多轮对话：传入完整消息历史，返回模型最后一条回答。
     *
     * @param messages 消息列表，按时间顺序，至少包含 1 条 user 消息
     * @return 模型生成的回答正文
     */
    public String chat(List<Message> messages) {
        if (props.getApiKey() == null || props.getApiKey().isBlank()) {
            throw new BizException(ErrorCode.AI_SERVICE_ERROR,
                    "DeepSeek API Key 未配置（campus.deepseek.api-key）");
        }
        if (messages == null || messages.isEmpty()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "消息列表不能为空");
        }

        ChatRequest body = new ChatRequest(
                props.getModel(),
                messages,
                props.getTemperature(),
                props.getMaxTokens(),
                false,
                thinkingConfig());

        ChatResponse response;
        try {
            response = restClient.post()
                    .uri("/chat/completions")
                    .body(body)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                        String errBody = readBodyQuietly(res);
                        log.error("DeepSeek 4xx 错误：status={} body={}", res.getStatusCode(), errBody);
                        throw new BizException(ErrorCode.AI_SERVICE_ERROR,
                                "DeepSeek 客户端错误：" + res.getStatusCode() + " " + truncate(errBody, 200));
                    })
                    .onStatus(HttpStatusCode::is5xxServerError, (req, res) -> {
                        String errBody = readBodyQuietly(res);
                        log.error("DeepSeek 5xx 错误：status={} body={}", res.getStatusCode(), errBody);
                        throw new BizException(ErrorCode.AI_SERVICE_ERROR,
                                "DeepSeek 服务暂不可用：" + res.getStatusCode());
                    })
                    .body(ChatResponse.class);
        } catch (HttpStatusCodeException e) {
            // 兜底：RestClient 某些版本在 status handler 抛 BizException 前可能先包装一层
            throw new BizException(ErrorCode.AI_SERVICE_ERROR,
                    "DeepSeek 调用失败：" + e.getStatusCode() + " " + truncate(e.getResponseBodyAsString(), 200), e);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("DeepSeek 调用异常", e);
            throw new BizException(ErrorCode.AI_SERVICE_ERROR, "DeepSeek 调用异常：" + e.getMessage(), e);
        }

        if (response == null
                || response.choices == null
                || response.choices.isEmpty()
                || response.choices.get(0).message == null) {
            throw new BizException(ErrorCode.AI_SERVICE_ERROR, "DeepSeek 响应为空或结构异常");
        }
        String content = response.choices.get(0).message.content;
        if (content == null) {
            content = "";
        }
        log.debug("DeepSeek 调用完成：tokens={} finish={}",
                response.usage != null ? response.usage.totalTokens : "n/a",
                response.choices.get(0).finishReason);
        return content.trim();
    }

    /**
     * 流式对话：把模型逐段吐出的增量文本通过 {@code onDelta} 回调实时交出去。
     *
     * <p>本方法<b>阻塞</b>直到流结束，由调用方决定在哪个线程上执行。
     * Spring MVC 场景下无需引入 WebFlux，直接在 SSE 工作线程里跑即可。</p>
     *
     * @param systemPrompt 系统提示词
     * @param userPrompt   用户消息
     * @param onDelta      每收到一段增量文本回调一次（不会收到空串）
     * @throws BizException 鉴权失败、配额不足、网络异常时抛出
     */
    public void streamChat(String systemPrompt, String userPrompt, Consumer<String> onDelta) {
        streamChat(List.of(
                new Message("system", systemPrompt),
                new Message("user", userPrompt)), onDelta);
    }

    /**
     * 流式对话（多轮）。行为同 {@link #streamChat(String, String, Consumer)}。
     */
    public void streamChat(List<Message> messages, Consumer<String> onDelta) {
        if (props.getApiKey() == null || props.getApiKey().isBlank()) {
            throw new BizException(ErrorCode.AI_SERVICE_ERROR,
                    "DeepSeek API Key 未配置（campus.deepseek.api-key）");
        }
        if (messages == null || messages.isEmpty()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "消息列表不能为空");
        }

        ChatRequest body = new ChatRequest(
                props.getModel(),
                messages,
                props.getTemperature(),
                props.getMaxTokens(),
                true,
                thinkingConfig());

        try {
            restClient.post()
                    .uri("/chat/completions")
                    .accept(MediaType.TEXT_EVENT_STREAM)
                    .body(body)
                    .exchange((req, res) -> {
                        if (res.getStatusCode().isError()) {
                            String errBody = readBodyQuietly(res);
                            log.error("DeepSeek 流式 4xx/5xx：status={} body={}",
                                    res.getStatusCode(), truncate(errBody, 300));
                            throw new BizException(ErrorCode.AI_SERVICE_ERROR,
                                    "DeepSeek 客户端错误：" + res.getStatusCode() + " " + truncate(errBody, 200));
                        }
                        // 逐行读取 SSE：形如 "data: {json}"，以 "data: [DONE]" 结束
                        try (BufferedReader reader = new BufferedReader(
                                new InputStreamReader(res.getBody(), StandardCharsets.UTF_8))) {
                            String line;
                            while ((line = reader.readLine()) != null) {
                                if (!line.startsWith("data:")) {
                                    continue;
                                }
                                String payload = line.substring(5).trim();
                                if (payload.isEmpty()) {
                                    continue;
                                }
                                if ("[DONE]".equals(payload)) {
                                    break;
                                }
                                String delta = parseDelta(payload);
                                if (delta != null && !delta.isEmpty()) {
                                    onDelta.accept(delta);
                                }
                            }
                        }
                        return null;
                    });
            log.debug("DeepSeek 流式调用完成");
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("DeepSeek 流式调用异常", e);
            throw new BizException(ErrorCode.AI_SERVICE_ERROR, "DeepSeek 调用异常：" + e.getMessage(), e);
        }
    }

    // ============================================================
    // Wire types（OpenAI 兼容协议，复用 Jackson 解析）
    // ============================================================

    /**
     * 单条对话消息。{@code role} 取值 {@code system | user | assistant}。
     */
    public record Message(String role, String content) {
    }

    /** 请求体 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ChatRequest(
            String model,
            List<Message> messages,
            double temperature,
            @JsonProperty("max_tokens") int maxTokens,
            boolean stream,
            /** 思考模式开关（DeepSeek V4）：{"type":"disabled"} 关闭思维链，null 表示不传（走服务端默认开启） */
            @JsonProperty("thinking") Thinking thinking) {
    }

    /** 思考模式配置：type 取 {@code enabled | disabled} */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Thinking(String type) {
    }

    /** 响应体（只解析用到的字段，其余忽略） */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ChatResponse(
            String id,
            List<Choice> choices,
            Usage usage) {

        @JsonIgnoreProperties(ignoreUnknown = true)
        public record Choice(
                int index,
                Message message,
                @JsonProperty("finish_reason") String finishReason) {
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        public record Usage(
                @JsonProperty("prompt_tokens") int promptTokens,
                @JsonProperty("completion_tokens") int completionTokens,
                @JsonProperty("total_tokens") int totalTokens) {
        }
    }

    /** 流式分片响应体（OpenAI 兼容：choices[0].delta.content） */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record StreamChunk(List<StreamChoice> choices) {

        @JsonIgnoreProperties(ignoreUnknown = true)
        public record StreamChoice(Delta delta) {
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        public record Delta(String content) {
        }
    }

    // ============================================================
    // 工具
    // ============================================================

    /**
     * 思考模式开关。
     *
     * <p>DeepSeek V4 思考模式默认开启且 effort=high，思维链跑完才开始吐正文，
     * 首字延迟大；本项目默认关闭（见 {@link DeepSeekProperties#isThinkingEnabled()}）。
     * 无论开关都显式传参，避免依赖服务端默认值。</p>
     */
    private Thinking thinkingConfig() {
        return new Thinking(props.isThinkingEnabled() ? "enabled" : "disabled");
    }

    /**
     * 从一条 {@code data:} 载荷里取出增量文本。
     * <p>解析失败（心跳、注释、非预期结构）时静默忽略，不影响整体流。</p>
     */
    private static String parseDelta(String payload) {
        try {
            StreamChunk chunk = MAPPER.readValue(payload, StreamChunk.class);
            if (chunk.choices() == null || chunk.choices().isEmpty()) {
                return null;
            }
            StreamChunk.Delta delta = chunk.choices().get(0).delta();
            return delta == null ? null : delta.content();
        } catch (Exception e) {
            log.debug("忽略无法解析的流式分片：{}", truncate(payload, 120));
            return null;
        }
    }

    private static String readBodyQuietly(org.springframework.http.client.ClientHttpResponse res) {
        try {
            return new String(res.getBody().readAllBytes(),
                    java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "<unreadable: " + e.getMessage() + ">";
        }
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }

    /** 当前配置的超时（供调用方参考） */
    public Duration getTimeout() {
        return timeout;
    }
}
