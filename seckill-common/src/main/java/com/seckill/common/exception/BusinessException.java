package com.seckill.common.exception;

import com.seckill.common.result.ResultCode;

public class BusinessException extends RuntimeException {
    private final int code;

    public BusinessException(ResultCode resultCode) {
        super(resultCode.message());
        this.code = resultCode.code();
    }

    public BusinessException(ResultCode resultCode, String message) {
        super(message);
        this.code = resultCode.code();
    }

    public int getCode() {
        return code;
    }
}
