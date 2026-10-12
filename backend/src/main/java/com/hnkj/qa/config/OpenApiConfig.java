package com.hnkj.qa.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 接口文档配置。访问 /api/swagger-ui.html
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI campusQaOpenAPI() {
        return new OpenAPI().info(new Info()
                .title("湖南科技职业学院 · 校园问答系统 API")
                .description("学校规章制度 PDF 智能问答服务")
                .version("v0.0.1")
                .contact(new Contact().name("Campus QA Team")));
    }
}
