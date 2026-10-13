package com.hnkj.qa.controller;

import com.hnkj.qa.common.api.ApiResponse;
import com.hnkj.qa.dto.response.UploadResult;
import com.hnkj.qa.service.DocumentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * PDF 上传接口。
 *
 * <p>完整地址：POST /api/upload（服务配置了 context-path=/api）</p>
 */
@Tag(name = "文件上传", description = "上传规章制度 PDF，保存到 uploads/ 并提取文字")
@RestController
@RequestMapping("/upload")
@RequiredArgsConstructor
public class UploadController {

    private final DocumentService documentService;

    @Operation(summary = "上传规章制度 PDF",
            description = "接收 multipart 文件（字段名 file），保存到 uploads/ 目录，"
                    + "用 Apache PDFBox 提取文字并打印到控制台，返回文件名与提取的文字长度")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<UploadResult> upload(@RequestParam("file") MultipartFile file) {
        return ApiResponse.ok(documentService.upload(file));
    }
}
