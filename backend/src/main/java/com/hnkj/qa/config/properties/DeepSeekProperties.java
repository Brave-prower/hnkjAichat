package com.hnkj.qa.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * DeepSeek 大模型客户端配置。
 *
 * <p>对应 {@code application.yml} 中 {@code campus.deepseek.*} 段。
 * 通过环境变量注入：{@code DEEPSEEK_API_KEY} / {@code DEEPSEEK_BASE_URL} / {@code DEEPSEEK_MODEL}。</p>
 *
 * <p>DeepSeek 的 API 兼容 OpenAI Chat Completions 协议，因此客户端实现
 * 与 OpenAI 几乎一致，仅 base-url 与模型名不同。</p>
 */
@Data
@ConfigurationProperties(prefix = "campus.deepseek")
public class DeepSeekProperties {

    /**
     * DeepSeek API Key。
     * <p>留空时客户端会直接抛出 {@link IllegalStateException}，避免默默调用到错误的账号。</p>
     */
    private String apiKey = "";

    /**
     * DeepSeek API 入口。
     * <p>默认官方地址；如使用代理或自建网关，覆盖此值。</p>
     */
    private String baseUrl = "https://api.deepseek.com/v1";

    /**
     * 对话模型名。默认 {@code deepseek-chat}（即 DeepSeek-V3）。
     * <p>可选：{@code deepseek-reasoner}（R1 推理模型）。</p>
     */
    private String model = "deepseek-chat";

    /** 单次请求超时（秒） */
    private int timeoutSeconds = 60;

    /** 采样温度，越低越确定；0 表示几乎贪婪 */
    private double temperature = 0.2;

    /** 单次回答最大 token 数 */
    private int maxTokens = 2000;

    /**
     * 是否开启思考模式（思维链）。
     *
     * <p>DeepSeek V4 系列<b>默认开启且推理强度为 high</b>：模型先输出一段思维链
     * （reasoning_content）再输出正文，首字延迟会显著变大。对"查规定"这类
     * 事实型问答收益很低，因此本项目默认关闭，需要深度推理时再打开。</p>
     */
    private boolean thinkingEnabled = false;
}
