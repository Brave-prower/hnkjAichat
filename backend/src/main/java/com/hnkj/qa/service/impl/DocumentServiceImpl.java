package com.hnkj.qa.service.impl;

import com.hnkj.qa.common.api.PageResult;
import com.hnkj.qa.common.enums.ErrorCode;
import com.hnkj.qa.common.exception.BizException;
import com.hnkj.qa.dto.response.DocumentVO;
import com.hnkj.qa.repository.DocumentRepository;
import com.hnkj.qa.service.DocumentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文档服务实现 —— 骨架版本，业务逻辑待填充。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentServiceImpl implements DocumentService {

    private final DocumentRepository documentRepository;
    // TODO 依赖注入：PdfIngestService（解析+向量化）、VectorStore（删除向量）

    @Override
    public DocumentVO upload(MultipartFile file, String title) {
        // TODO 1. 校验扩展名与文件头
        // TODO 2. 落盘到 campus.storage.local-path
        // TODO 3. 保存 Document 记录（status=PENDING）
        // TODO 4. 异步调用 PdfIngestService 解析并写入向量库，更新 status=READY
        throw new BizException(ErrorCode.DOCUMENT_UPLOAD_FAILED, "上传功能待实现");
    }

    @Override
    public PageResult<DocumentVO> page(int page, int size, String keyword) {
        // TODO 结合 DocumentRepository 分页查询并转换为 DocumentVO
        throw new BizException(ErrorCode.SYSTEM_ERROR, "查询功能待实现");
    }

    @Override
    public DocumentVO detail(Long id) {
        // TODO 查询单条文档
        throw new BizException(ErrorCode.DOCUMENT_NOT_FOUND);
    }

    @Override
    public void delete(Long id) {
        // TODO 逻辑删除记录 + 按 documentId 过滤删除 vector_store 中的向量
        throw new BizException(ErrorCode.DOCUMENT_NOT_FOUND);
    }

    @Override
    public DocumentVO reindex(Long id) {
        // TODO 清空旧向量后重新解析入库
        throw new BizException(ErrorCode.DOCUMENT_NOT_FOUND);
    }
}
