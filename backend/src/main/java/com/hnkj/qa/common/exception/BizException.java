package com.hnkj.qa.common.exception;

import com.hnkj.qa.common.enums.ErrorCode;
import lombok.Getter;

/**
 * 业务异常。Service 层抛出，由 {@link GlobalExceptionHandler} 统一转换为响应体。
 */
@Getter
public class BizException extends RuntimeException {

    private final int code;

    public BizException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.code = errorCode.getCode();
    }

    public BizException(ErrorCode errorCode, String message) {
        super(message);
        this.code = errorCode.getCode();
    }

    public BizException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.code = errorCode.getCode();
    }
}
