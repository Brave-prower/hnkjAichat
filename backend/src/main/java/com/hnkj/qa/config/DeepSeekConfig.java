package com.hnkj.qa.config;

import com.hnkj.qa.ai.deepseek.DeepSeekClient;
import com.hnkj.qa.config.properties.DeepSeekProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * DeepSeek 客户端装配。
 *
 * <p>用 Spring 6 提供的 {@link RestClient}（同步 HTTP 客户端）作为底层，
 * 不引入 OkHttp / WebClient 额外依赖。RestClient 默认走 JDK HttpClient，
 * 在 Spring Boot 3.2+ 可直接使用。</p>
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(DeepSeekProperties.class)
public class DeepSeekConfig {

    @Bean
    public RestClient deepSeekRestClient(DeepSeekProperties props) {
        if (props.getApiKey() == null || props.getApiKey().isBlank()) {
            log.warn("[DeepSeek] API Key 未配置（campus.deepseek.api-key），调用时会失败。"
                    + "请通过环境变量 DEEPSEEK_API_KEY 注入。");
        }
        return RestClient.builder()
                .baseUrl(props.getBaseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + props.getApiKey())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @Bean
    public DeepSeekClient deepSeekClient(RestClient deepSeekRestClient, DeepSeekProperties props) {
        return new DeepSeekClient(deepSeekRestClient, props,
                Duration.ofSeconds(props.getTimeoutSeconds()));
    }
}
