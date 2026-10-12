package com.hnkj.qa.controller;

import com.hnkj.qa.common.api.ApiResponse;
import com.hnkj.qa.common.api.PageResult;
import com.hnkj.qa.dto.response.DocumentVO;
import com.hnkj.qa.service.DocumentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 规章制度文档接口。
 */
@Tag(name = "文档管理", description = "规章制度 PDF 的上传、查询、删除与重建索引")
@RestController
@RequestMapping("/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;

    @Operation(summary = "上传规章制度 PDF")
    @PostMapping("/upload")
    public ApiResponse<DocumentVO> upload(@RequestParam("file") MultipartFile file,
                                          @RequestParam(value = "title", required = false) String title) {
        return ApiResponse.ok(documentService.upload(file, title));
    }

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
