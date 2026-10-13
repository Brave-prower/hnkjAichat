package com.hnkj.qa.service.impl;

import com.hnkj.qa.ai.PdfIngestService;
import com.hnkj.qa.common.api.PageResult;
import com.hnkj.qa.common.enums.ErrorCode;
import com.hnkj.qa.common.exception.BizException;
import com.hnkj.qa.config.properties.StorageProperties;
import com.hnkj.qa.dto.response.DocumentVO;
import com.hnkj.qa.dto.response.UploadResult;
import com.hnkj.qa.repository.DocumentRepository;
import com.hnkj.qa.service.DocumentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;

/**
 * 文档服务实现。
 *
 * <p>upload 已实现：校验 → 落盘 uploads/ → PDFBox 提取并打印控制台；
 * 分页 / 详情 / 删除 / 重建索引 待后续补充。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentServiceImpl implements DocumentService {

    private final DocumentRepository documentRepository;
    private final PdfIngestService pdfIngestService;
    private final StorageProperties storageProperties;

    @Override
    public UploadResult upload(MultipartFile file) {
        // ---------- 1. 参数校验 ----------
        if (file == null || file.isEmpty()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "上传文件不能为空");
        }
        String originalName = StringUtils.cleanPath(
                Objects.requireNonNullElse(file.getOriginalFilename(), "unnamed.pdf"));
        if (!originalName.toLowerCase().endsWith(".pdf")) {
            throw new BizException(ErrorCode.DOCUMENT_TYPE_NOT_ALLOWED);
        }

        // ---------- 2. 保存到 uploads/ 目录 ----------
        Path dir = Paths.get(storageProperties.getLocalPath()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new BizException(ErrorCode.DOCUMENT_UPLOAD_FAILED, "创建上传目录失败", e);
        }
        // 加时间戳前缀避免重名覆盖，同时过滤文件名中的非法字符
        String storedName = System.currentTimeMillis() + "_"
                + originalName.replaceAll("[\\\\/:*?\"<>|\\s]", "_");
        Path target = dir.resolve(storedName);
        try {
            file.transferTo(target);
        } catch (IOException e) {
            throw new BizException(ErrorCode.DOCUMENT_UPLOAD_FAILED,
                    "文件保存失败：" + e.getMessage(), e);
        }
        log.info("文件已保存：{}（{} 字节）", target, file.getSize());

        // ---------- 3/4. PDFBox 提取文字并打印到控制台 ----------
        PdfIngestService.PdfContent content = pdfIngestService.extract(target);

        // ---------- 5. 返回文件名与提取的文字长度 ----------
        return UploadResult.builder()
                .fileName(originalName)
                .storedName(storedName)
                .textLength(content.text().length())
                .pageCount(content.pageCount())
                .build();
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
