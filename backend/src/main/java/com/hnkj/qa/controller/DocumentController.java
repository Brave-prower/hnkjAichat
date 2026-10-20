package com.hnkj.qa.controller;

import com.hnkj.qa.common.api.ApiResponse;
import com.hnkj.qa.common.api.PageResult;
import com.hnkj.qa.dto.response.ChunkVO;
import com.hnkj.qa.dto.response.DocumentVO;
import com.hnkj.qa.service.DocumentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 规章制度文档接口。
 *
 * <p>上传接口见 {@link UploadController}（POST /upload），此处保留查询、预览与维护操作。</p>
 */
@Tag(name = "文档管理", description = "规章制度 PDF 的查询、预览、删除与重建索引")
@RestController
@RequestMapping("/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;

    @Operation(summary = "分页查询文档")
    @GetMapping
    public ApiResponse<PageResult<DocumentVO>> page(@RequestParam(defaultValue = "1") int page,
                                                    @RequestParam(defaultValue = "10") int size,
                                                    @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(documentService.page(page, size, keyword));
    }

    @Operation(summary = "查询文档详情")
    @GetMapping("/{id}")
    public ApiResponse<DocumentVO> detail(@PathVariable Long id) {
        return ApiResponse.ok(documentService.detail(id));
    }

    @Operation(summary = "查询文档的文本块列表",
            description = "返回切分后的全部文本块（页码、内容、是否已向量化），用于核对入库结果")
    @GetMapping("/{id}/chunks")
    public ApiResponse<List<ChunkVO>> chunks(@PathVariable Long id) {
        return ApiResponse.ok(documentService.chunks(id));
    }

    /**
     * 返回 PDF 原始文件，供浏览器内嵌预览或下载。
     *
     * <p>要点：
     * <ul>
     *   <li>{@code Content-Disposition: inline} 让浏览器直接渲染而非弹下载框；</li>
     *   <li>文件名用 RFC 5987 的 {@code filename*=UTF-8''…} 编码，否则中文名在
     *       下载时会变成乱码或被截断；</li>
     *   <li>{@code Content-Length} 显式给出，浏览器 PDF 阅读器才能显示进度；
     *       同时支持 Range 请求以便大文件分段加载。</li>
     * </ul>
     */
    @Operation(summary = "获取文档 PDF 原文", description = "在线预览或下载原始 PDF")
    @GetMapping("/{id}/file")
    public ResponseEntity<Resource> file(@PathVariable Long id,
                                         @RequestParam(defaultValue = "false") boolean download) {
        DocumentService.DocumentFile file = documentService.file(id);

        // inline 预览 / attachment 下载，两种场景共用同一接口
        ContentDisposition contentDisposition = download
                ? ContentDisposition.attachment().filename(file.fileName(), StandardCharsets.UTF_8).build()
                : ContentDisposition.inline().filename(file.fileName(), StandardCharsets.UTF_8).build();

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .contentLength(file.size())
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition.toString())
                // 企业内网缓存一天；文件内容不可变（每次上传都是新文件），可放心缓存
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=86400")
                .body(file.resource());
    }

    @Operation(summary = "删除文档")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        documentService.delete(id);
        return ApiResponse.ok();
    }

    @Operation(summary = "重建文档向量索引")
    @PostMapping("/{id}/reindex")
    public ApiResponse<DocumentVO> reindex(@PathVariable Long id) {
        return ApiResponse.ok(documentService.reindex(id));
    }
}
