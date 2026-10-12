package com.hnkj.qa.service;

import com.hnkj.qa.common.api.PageResult;
import com.hnkj.qa.dto.response.DocumentVO;
import org.springframework.web.multipart.MultipartFile;

/**
 * 规章制度文档服务。
 */
public interface DocumentService {

    /**
     * 上传 PDF 并触发解析、切分、向量化流程。
     *
     * @param file  上传的 PDF 文件
     * @param title 文档标题，为空时取文件名
     * @return 文档信息
     */
    DocumentVO upload(MultipartFile file, String title);

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
