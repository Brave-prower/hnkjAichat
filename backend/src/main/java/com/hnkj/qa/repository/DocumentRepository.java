package com.hnkj.qa.repository;

import com.hnkj.qa.entity.Document;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 文档数据访问层。
 */
@Repository
public interface DocumentRepository extends JpaRepository<Document, Long> {

    Page<Document> findAllByDeletedFalseOrderByCreatedAtDesc(Pageable pageable);

    /** 标题模糊搜索（忽略大小写） */
    Page<Document> findAllByDeletedFalseAndTitleContainingIgnoreCaseOrderByCreatedAtDesc(
            String title, Pageable pageable);

    Page<Document> findAllByDeletedFalse(Pageable pageable);

    Optional<Document> findByIdAndDeletedFalse(Long id);

    boolean existsByFileNameAndDeletedFalse(String fileName);
}
