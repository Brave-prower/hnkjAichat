package com.hnkj.qa.common.enums;

import lombok.Getter;

/**
 * 业务错误码。
 *
 * <p>约定：0 成功；1xxxx 通用错误；2xxxx 文档相关；3xxxx 问答相关。</p>
 */
@Getter
public enum ErrorCode {

    SUCCESS(0, "操作成功"),

    // ---------- 通用 ----------
    BAD_REQUEST(10001, "请求参数不合法"),
    UNAUTHORIZED(10002, "未登录或登录已过期"),
    FORBIDDEN(10003, "没有权限执行该操作"),
    NOT_FOUND(10004, "请求的资源不存在"),
    SYSTEM_ERROR(10500, "系统繁忙，请稍后重试"),

    // ---------- 文档 ----------
    DOCUMENT_NOT_FOUND(20001, "文档不存在"),
    DOCUMENT_TYPE_NOT_ALLOWED(20002, "只支持上传 PDF 格式的规章制度文件"),
    DOCUMENT_UPLOAD_FAILED(20003, "文件上传失败"),
    DOCUMENT_PARSE_FAILED(20004, "文档解析失败"),
    DOCUMENT_DUPLICATED(20005, "该文档已存在"),

    // ---------- 问答 ----------
    QUESTION_EMPTY(30001, "提问内容不能为空"),
    NO_REFERENCE_FOUND(30002, "知识库中未检索到相关内容"),
    AI_SERVICE_ERROR(30003, "智能服务暂时不可用，请稍后重试");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
