package com.hnkj.qa.service.impl;

import com.hnkj.qa.ai.PdfIngestService;
import com.hnkj.qa.common.api.PageResult;
import com.hnkj.qa.common.enums.ErrorCode;
import com.hnkj.qa.common.exception.BizException;
import com.hnkj.qa.config.properties.StorageProperties;
import com.hnkj.qa.dto.response.DocumentVO;
import com.hnkj.qa.dto.response.UploadResult;
import com.hnkj.qa.entity.Document;
import com.hnkj.qa.repository.DocumentRepository;
import com.hnkj.qa.service.DocumentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

/**
 * 文档服务实现。
 *
 * <p>已实现：upload（上传+向量入库，失败优雅降级）、page（分页+标题搜索）、
 * detail（详情）、delete（逻辑删除+向量清理）、reindex（重建索引）。</p>
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
        // 物理文件名一律使用纯 ASCII（时间戳 + UUID），原始文件名只存数据库。
        // 原因：
        //   1) 容器/宿主机 locale 不是 UTF-8 时，中文文件名构造 Path 会抛
        //      InvalidPathException（Android/Temurin slim 镜像常见）；
        //   2) 天然规避路径穿越（../）、超长文件名、Windows 非法字符等问题。
        String storedName = System.currentTimeMillis() + "_"
                + UUID.randomUUID().toString().replace("-", "") + ".pdf";
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

        // ---------- 5. 文档元信息落库 ----------
        // 标题 = 去掉扩展名的原始文件名
        String title = StringUtils.hasText(originalName)
                ? originalName.replaceAll("\\.[^.]+$", "")
                : originalName;
        Document document = Document.builder()
                .title(title)
                .fileName(originalName)
                .filePath(target.toString())
                .fileSize(file.getSize())
                .pageCount(content.pageCount())
                .status("PARSING")
                .build();
        document = documentRepository.save(document);

        // ---------- 6. 向量入库（local=普通表余弦 / railway=pgvector，实现透明） ----------
        // 失败不阻断上传：文档与提取结果已保存，状态降级为 FAILED，可后续调 reindex 重试
        int chunkCount = 0;
        String status = "READY";
        try {
            chunkCount = pdfIngestService.ingest(new FileSystemResource(target), document.getId(), title);
            document.setStatus("READY");
        } catch (Exception e) {
            status = "FAILED";
            document.setStatus("FAILED");
            document.setRemark("向量入库失败：" + rootMessage(e));
            log.error("文档 [{}] 向量入库失败（文件已保存，可稍后 reindex 重试）：{}",
                    document.getId(), rootMessage(e), e);
        }
        documentRepository.save(document);

        // ---------- 7. 返回上传结果 ----------
        return UploadResult.builder()
                .fileName(originalName)
                .storedName(storedName)
                .textLength(content.text().length())
                .pageCount(content.pageCount())
                .documentId(document.getId())
                .status(status)
                .chunkCount(chunkCount)
                .build();
    }

    /** 取异常链最底层的可读消息 */
    private String rootMessage(Throwable e) {
        Throwable t = e;
        while (t.getCause() != null && t.getCause() != t) {
            t = t.getCause();
        }
        return t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
    }

    @Override
    public PageResult<DocumentVO> page(int page, int size, String keyword) {
        // 参数兜底：page 从 1 开始，size 限制在 [1, 100]
        int pageNo = Math.max(page, 1) - 1;
        int pageSize = Math.min(Math.max(size, 1), 100);
        Pageable pageable = PageRequest.of(pageNo, pageSize);

        Page<Document> result = StringUtils.hasText(keyword)
                ? documentRepository.findAllByDeletedFalseAndTitleContainingIgnoreCaseOrderByCreatedAtDesc(keyword, pageable)
                : documentRepository.findAllByDeletedFalse(pageable);

        return PageResult.of(result, this::toVO);
    }

    @Override
    public DocumentVO detail(Long id) {
        Document document = findActive(id);
        return toVO(document);
    }

    @Override
    public void delete(Long id) {
        Document document = findActive(id);

        // 1. 清理向量库中该文档的全部片段（失败不阻断逻辑删除，可重试）
        try {
            pdfIngestService.deleteByDocumentId(id);
        } catch (Exception e) {
            log.warn("文档 [{}] 向量清理失败（逻辑删除继续）：{}", id, rootMessage(e));
        }

        // 2. 逻辑删除文档记录
        document.setDeleted(true);
        documentRepository.save(document);
        log.info("文档 [{}] 已删除：{}", id, document.getTitle());
    }

    @Override
    public DocumentVO reindex(Long id) {
        Document document = findActive(id);
        Path pdfPath = Paths.get(document.getFilePath());
        if (!Files.exists(pdfPath)) {
            throw new BizException(ErrorCode.DOCUMENT_NOT_FOUND, "原文件已不存在，无法重建索引");
        }

        // 1. 清空旧向量，避免重复入库
        try {
            pdfIngestService.deleteByDocumentId(id);
        } catch (Exception e) {
            log.warn("文档 [{}] 旧向量清理失败（继续重建）：{}", id, rootMessage(e));
        }

        // 2. 重新解析入库，按结果刷新状态
        try {
            int chunkCount = pdfIngestService.ingest(new FileSystemResource(pdfPath), id, document.getTitle());
            document.setStatus("READY");
            document.setRemark(null);
            log.info("文档 [{}] 重建索引完成：{} 个文本块", id, chunkCount);
        } catch (Exception e) {
            document.setStatus("FAILED");
            document.setRemark("重建索引失败：" + rootMessage(e));
            log.error("文档 [{}] 重建索引失败：{}", id, rootMessage(e), e);
        }
        documentRepository.save(document);
        return toVO(document);
    }

    // ---------- 内部工具 ----------

    /** 查询未删除的文档，不存在则抛 DOCUMENT_NOT_FOUND */
    private Document findActive(Long id) {
        return documentRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new BizException(ErrorCode.DOCUMENT_NOT_FOUND));
    }

    /** 实体 → 列表 VO */
    private DocumentVO toVO(Document document) {
        return DocumentVO.builder()
                .id(document.getId())
                .title(document.getTitle())
                .fileName(document.getFileName())
                .fileSize(document.getFileSize())
                .pageCount(document.getPageCount())
                .status(document.getStatus())
                .createdAt(document.getCreatedAt())
                .build();
    }
}
