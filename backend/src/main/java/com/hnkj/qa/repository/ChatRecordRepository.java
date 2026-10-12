package com.hnkj.qa.repository;

import com.hnkj.qa.entity.ChatRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 问答记录数据访问层。
 */
@Repository
public interface ChatRecordRepository extends JpaRepository<ChatRecord, Long> {

    List<ChatRecord> findBySessionIdOrderByCreatedAtAsc(String sessionId);

    Page<ChatRecord> findBySessionId(String sessionId, Pageable pageable);
}
