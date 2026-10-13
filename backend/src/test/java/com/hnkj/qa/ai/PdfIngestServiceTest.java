package com.hnkj.qa.ai;

import com.hnkj.qa.config.properties.QaProperties;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PDF 提取逻辑单元测试（不依赖 Spring 容器与数据库）。
 */
class PdfIngestServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldExtractTextFromGeneratedPdf() throws Exception {
        // ---- 生成一个两页的测试 PDF ----
        Path pdf = tempDir.resolve("sample.pdf");
        try (PDDocument doc = new PDDocument()) {
            String[] pageTexts = {"Hello Campus QA", "Student Handbook Rules"};
            for (String line : pageTexts) {
                PDPage page = new PDPage();
                doc.addPage(page);
                try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                    cs.beginText();
                    cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                    cs.newLineAtOffset(50, 700);
                    cs.showText(line);
                    cs.endText();
                }
            }
            doc.save(pdf.toFile());
        }

        // ---- 调用被测方法 ----
        PdfIngestService service = new PdfIngestService(null, new QaProperties());
        PdfIngestService.PdfContent content = service.extract(pdf);

        // ---- 断言 ----
        assertEquals(2, content.pageCount());
        assertTrue(content.text().contains("Hello Campus QA"), "应包含第 1 页文字");
        assertTrue(content.text().contains("Student Handbook Rules"), "应包含第 2 页文字");
        assertFalse(content.text().isBlank());
        assertTrue(content.text().length() > 0);

        // ---- 留一份样例文件到 target/，供后续接口联调（curl）使用 ----
        Path keep = Path.of("target", "sample.pdf");
        Files.createDirectories(keep.getParent());
        Files.copy(pdf, keep, StandardCopyOption.REPLACE_EXISTING);
    }
}
