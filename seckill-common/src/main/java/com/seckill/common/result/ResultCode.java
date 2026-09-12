package com.seckill.common.result;

public enum ResultCode {
    SUCCESS(0, "ok"),
    UNAUTHORIZED(401, "未登录"),
    SOLD_OUT(1001, "已抢完"),
    RATE_LIMITED(1002, "请求过于频繁"),
    NOT_STARTED(1003, "活动未开始"),
    DUPLICATE(1004, "请勿重复下单");

    private final int code;
    private final String message;

    ResultCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int code() {
        return code;
    }

    public String message() {
        return message;
    }
}
