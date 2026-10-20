package com.hnkj.qa.service;

import com.hnkj.qa.common.api.PageResult;
import com.hnkj.qa.dto.response.ChunkVO;
import com.hnkj.qa.dto.response.DocumentVO;
import com.hnkj.qa.dto.response.UploadResult;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

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

    /**
     * 查询某文档切分后的全部文本块，按页码与块序排列。
     *
     * @param id 文档 ID
     * @return 文本块列表（不含向量，避免大对象传输）
     */
    List<ChunkVO> chunks(Long id);

    /**
     * 解析文档对应的 PDF 物理文件，供在线预览/下载。
     *
     * <p>会校验文件确实存在于配置的上传目录内，杜绝路径穿越。</p>
     *
     * @param id 文档 ID
     * @return 可读的 PDF 资源
     */
    DocumentFile file(Long id);

    /**
     * 文档物理文件信息。
     *
     * @param fileName 原始文件名（用于 Content-Disposition）
     * @param resource PDF 文件资源
     * @param size     文件字节数
     */
    record DocumentFile(String fileName, org.springframework.core.io.Resource resource, long size) {
    }
}
