package com.hnkj.qa.ai;

import com.hnkj.qa.common.enums.ErrorCode;
import com.hnkj.qa.common.exception.BizException;
import com.hnkj.qa.config.properties.QaProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;

/**
 * PDF 解析与入库管线。
 *
 * <p><b>当前阶段</b>：提供 {@link #extract(Path)}，使用 Apache PDFBox 提取全文并打印到控制台，
 * 暂不写入向量库；后续再启用 {@link #ingest(Resource, Long, String)} 完成向量化。</p>
 *
 * <p><b>页码溯源关键点</b>：{@link PagePdfDocumentReader} 会在每个 Document 的 metadata
 * 中写入 {@code page_number}（从 1 开始）。在切分后需要把该字段透传到每个文本块，
 * 并额外补充 {@code documentId} / {@code title}，检索时才能回溯到具体文档与页码。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PdfIngestService {

    /** 向量库中标记文档 ID 的元数据键 */
    public static final String META_DOCUMENT_ID = "documentId";
    /** 向量库中标记页码的元数据键（Spring AI PDF Reader 原生输出 page_number） */
    public static final String META_PAGE_NUMBER = "page_number";
    /** 向量库中标记文档标题的元数据键 */
    public static final String META_TITLE = "title";

    private final VectorStore vectorStore;
    private final QaProperties qaProperties;

    /**
     * PDF 提取结果。
     *
     * @param text      提取出的全文
     * @param pageCount 页数
     */
    public record PdfContent(String text, int pageCount) {
    }

    /**
     * 使用 Apache PDFBox 提取 PDF 全文，并打印到控制台。
     *
     * <p>当前阶段仅用于验证解析链路，暂不写入向量库。</p>
     *
     * @param pdfFile 本地 PDF 文件
     * @return 全文与页数
     * @throws BizException 文件损坏或无法解析时抛出 DOCUMENT_PARSE_FAILED
     */
    public PdfContent extract(Path pdfFile) {
        try (PDDocument document = Loader.loadPDF(pdfFile.toFile())) {
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(document);
            int pageCount = document.getNumberOfPages();

            log.info("===== PDF 文字提取开始：{}（共 {} 页，{} 字符）=====",
                    pdfFile.getFileName(), pageCount, text.length());
            if (text.isBlank()) {
                log.warn("未提取到文字，可能是扫描版/图片型 PDF：{}", pdfFile.getFileName());
            } else {
                // 需求 4：把提取的文字打印到控制台
                log.info("提取内容：\n{}", text.trim());
            }
            log.info("===== PDF 文字提取结束 =====");

            return new PdfContent(text, pageCount);
        } catch (IOException e) {
            throw new BizException(ErrorCode.DOCUMENT_PARSE_FAILED,
                    "PDF 解析失败：" + e.getMessage(), e);
        }
    }

    /**
     * 解析 PDF 并写入向量库。
     *
     * <p>向量库由 Spring AI 的 {@link VectorStore} 接口抽象：
     * local profile 走普通表 + 余弦检索，railway profile 走 pgvector，
     * 本方法对两种实现透明。</p>
     *
     * @param resource   PDF 资源
     * @param documentId 业务侧文档 ID
     * @param title      文档标题
     * @return 写入的文本块数量
     */
    public int ingest(Resource resource, Long documentId, String title) {
        // Step 1：按页读取，每页一个 Document，metadata 带 page_number（从 1 开始）
        PagePdfDocumentReader reader = new PagePdfDocumentReader(resource);
        List<Document> pages = reader.get();

        // Step 2：切分长文本（TokenTextSplitter 会把父级元数据合并进每个文本块）
        TokenTextSplitter splitter = new TokenTextSplitter();
        List<Document> chunks = splitter.apply(pages);

        // Step 3：补充业务元数据（documentId / title），保留原 page_number
        String documentIdStr = String.valueOf(documentId);
        List<Document> enriched = chunks.stream()
                .map(chunk -> Document.builder()
                        .id(chunk.getId())
                        .text(chunk.getText())
                        .metadata(new HashMap<>(chunk.getMetadata()))
                        .metadata(META_DOCUMENT_ID, documentIdStr)
                        .metadata(META_TITLE, title)
                        .build())
                .toList();

        // Step 4：向量入库（向量化由 VectorStore 实现内部调用 EmbeddingModel 完成）
        vectorStore.add(enriched);
        log.info("文档 [{}] 向量入库完成：{} 页 → {} 个文本块", title, pages.size(), enriched.size());
        return enriched.size();
    }

    /**
     * 按文档 ID 删除向量库中的全部片段。
     *
     * <p>local 实现：DELETE FROM t_vector_chunk WHERE metadata->>'documentId' = ?；<br>
     * pgvector 实现：等价的 delete filter 表达式。</p>
     */
    public void deleteByDocumentId(Long documentId) {
        Filter.Expression filter = new Filter.Expression(
                Filter.ExpressionType.EQ,
                new Filter.Key(META_DOCUMENT_ID),
                new Filter.Value(String.valueOf(documentId)));
        vectorStore.delete(filter);
        log.info("已清理文档 [{}] 的全部向量数据", documentId);
    }
}
