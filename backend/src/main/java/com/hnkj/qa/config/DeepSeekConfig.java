package com.hnkj.qa.config;

import com.hnkj.qa.ai.deepseek.DeepSeekClient;
import com.hnkj.qa.config.properties.DeepSeekProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * DeepSeek 客户端装配。
 *
 * <p>用 Spring 6 提供的 {@link RestClient}（同步 HTTP 客户端）作为底层，
 * 不引入 OkHttp / WebClient 额外依赖。RestClient 默认走 JDK HttpClient，
 * 在 Spring Boot 3.2+ 可直接使用。</p>
 *
 * <p><b>为什么要显式指定请求工厂</b>：默认的 JDK HttpClient 会复用长连接，服务端关闭空闲连接后
 * 客户端未必及时感知，下一次请求可能命中已死连接并抛 {@code Connection reset}——
 * 这正是本地网络下「偶发一次提问就降级兜底」的根因之一。这里显式指定 JDK 请求工厂并
 * 设置连接/读取超时，把失控的长时间挂起收敛为可判定的失败，再交给客户端重试兜住。</p>
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(DeepSeekProperties.class)
public class DeepSeekConfig {

    /** 网络类异常的最大重试次数（不含首次） */
    private static final int MAX_NETWORK_RETRY = 1;

    @Bean
    public RestClient deepSeekRestClient(DeepSeekProperties props) {
        if (props.getApiKey() == null || props.getApiKey().isBlank()) {
            log.warn("[DeepSeek] API Key 未配置（campus.deepseek.api-key），调用时会失败。"
                    + "请通过环境变量 DEEPSEEK_API_KEY 注入。");
        }

        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .version(HttpClient.Version.HTTP_1_1)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(props.getTimeoutSeconds()));

        return RestClient.builder()
                .baseUrl(props.getBaseUrl())
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + props.getApiKey())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @Bean
    public DeepSeekClient deepSeekClient(RestClient deepSeekRestClient, DeepSeekProperties props) {
        return new DeepSeekClient(deepSeekRestClient, props,
                Duration.ofSeconds(props.getTimeoutSeconds()), MAX_NETWORK_RETRY);
    }
}
