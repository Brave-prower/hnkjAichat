package com.hnkj.qa.ai;

import com.hnkj.qa.config.properties.QaProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * PDF 入库管线：读取 → 按页切分 → 向量化写入。
 *
 * <p>骨架版本：仅定义流程与元数据约定，具体实现待填充。</p>
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
     * 解析 PDF 并写入向量库。
     *
     * @param resource   PDF 资源
     * @param documentId 业务侧文档 ID
     * @param title      文档标题
     * @return 写入的文本块数量
     */
    public int ingest(Resource resource, Long documentId, String title) {
        // TODO Step 1：按页读取，保留 page_number 元数据
        PagePdfDocumentReader reader = new PagePdfDocumentReader(resource);
        List<Document> pages = reader.get();

        // TODO Step 2：切分长文本（保持元数据不丢失）
        TokenTextSplitter splitter = new TokenTextSplitter();
        List<Document> chunks = splitter.apply(pages);

        // TODO Step 3：补充业务元数据（documentId / title）
        // TODO Step 4：vectorStore.add(chunks)
        // TODO Step 5：返回 chunks.size()
        log.info("待实现：文档 [{}] 解析入库，共 {} 页", title, pages.size());
        return 0;
    }

    /**
     * 按文档 ID 删除向量库中的全部片段。
     */
    public void deleteByDocumentId(Long documentId) {
        // TODO vectorStore.delete("documentId == " + documentId)
        log.info("待实现：清理文档 [{}] 的向量数据", documentId);
    }
}
