package com.hnkj.qa;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 上下文加载测试。
 *
 * <p>注意：完整启动需要 PostgreSQL(pgvector) 可用，并已配置大模型 API Key。
 * 默认已 disabled，接入测试库后移除 {@code @Disabled} 注解。</p>
 */
@SpringBootTest
@org.junit.jupiter.api.Disabled("需要数据库与大模型环境，接入后开启")
class HnkjQaApplicationTests {

    @Test
    void contextLoads() {
    }
}
