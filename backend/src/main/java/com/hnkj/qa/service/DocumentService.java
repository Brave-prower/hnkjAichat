package com.hnkj.qa.service;

import com.hnkj.qa.common.api.PageResult;
import com.hnkj.qa.dto.response.DocumentVO;
import com.hnkj.qa.dto.response.UploadResult;
import org.springframework.web.multipart.MultipartFile;

/**
 * 规章制度文档服务。
 */
public interface DocumentService {

    /**
     * 上传 PDF：保存到 uploads/ 目录，用 PDFBox 提取文字并打印到控制台。
     * 当前阶段不写入向量库。
     *
     * @param file 上传的 PDF 文件
     * @return 文件名与提取的文字长度
     */
    UploadResult upload(MultipartFile file);

    /**
     * 分页查询文档列表。
     */
    PageResult<DocumentVO> page(int page, int size, String keyword);

    /**
     * 查询文档详情。
     */
    DocumentVO detail(Long id);

    /**
     * 删除文档（逻辑删除 + 清理向量）。
     */
    void delete(Long id);

    /**
     * 重新解析并重建向量索引。
     */
    DocumentVO reindex(Long id);
}
