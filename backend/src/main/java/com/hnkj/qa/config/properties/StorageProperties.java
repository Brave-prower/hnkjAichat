package com.hnkj.qa.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 文件存储配置，对应 application.yml 中 campus.storage.*
 */
@Data
@ConfigurationProperties(prefix = "campus.storage")
public class StorageProperties {

    /** 存储类型：local / minio / oss */
    private String type = "local";

    /** 本地存储根目录（PDF 上传落盘位置） */
    private String localPath = "./uploads";
}
