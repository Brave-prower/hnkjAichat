package com.hnkj.qa;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * 湖南科技职业学院 · 校园问答系统 启动类
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class HnkjQaApplication {

    public static void main(String[] args) {
        SpringApplication.run(HnkjQaApplication.class, args);
    }
}
