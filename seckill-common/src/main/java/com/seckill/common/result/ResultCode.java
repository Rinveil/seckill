package com.seckill.common.result;

public enum ResultCode {
    SUCCESS(0, "ok"),
    BAD_REQUEST(400, "参数错误"),
    UNAUTHORIZED(401, "未登录"),
    FORBIDDEN(403, "无权限"),
    SOLD_OUT(1001, "已抢完"),
    RATE_LIMITED(1002, "请求过于频繁"),
    NOT_STARTED(1003, "活动未开始"),
    DUPLICATE(1004, "请勿重复下单"),
    USERNAME_EXISTS(1005, "用户名已存在"),
    LOGIN_FAILED(1006, "用户名或密码错误");

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
