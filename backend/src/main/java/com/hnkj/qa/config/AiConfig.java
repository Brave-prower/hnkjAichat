package com.hnkj.qa.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

/**
 * Spring AI 配置。
 *
 * <p>ChatClient 由 Spring AI 自动装配的 {@link ChatModel} 构建，
 * 业务层直接注入使用。RAG 检索与提示词组装放在 {@code com.hnkj.qa.ai} 包中实现。</p>
 */
@Configuration
public class AiConfig {

    /** 系统提示词模板（ClassPath 下 prompts/*.st） */
    public static final Resource QA_SYSTEM_PROMPT = new ClassPathResource("prompts/qa-system-prompt.st");

    @Bean
    public ChatClient chatClient(ChatModel chatModel) {
        return ChatClient.builder(chatModel)
                .defaultSystem("你是湖南科技职业学院的规章制度咨询助手。")
                .build();
    }
}
