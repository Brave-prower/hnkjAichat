package com.hnkj.qa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 学校规章制度文档。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "t_document")
public class Document {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 文档标题 */
    @Column(nullable = false, length = 255)
    private String title;

    /** 原始文件名 */
    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    /** 存储路径 */
    @Column(name = "file_path", nullable = false, length = 512)
    private String filePath;

    /** 文件大小（字节） */
    @Column(name = "file_size")
    private Long fileSize;

    /** 页数 */
    @Column(name = "page_count")
    private Integer pageCount;

    /** 处理状态：PENDING / PARSING / READY / FAILED */
    @Column(nullable = false, length = 32)
    private String status;

    /** 备注 */
    @Column(length = 512)
    private String remark;

    /** 逻辑删除标记 */
    @Column(nullable = false)
    @Builder.Default
    private Boolean deleted = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
