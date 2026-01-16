package com.nowcoder.community.common.api;

public enum CommonErrorCode implements ErrorCode {
    SUCCESS(0, "OK"),

    BAD_REQUEST(40000, "请求参数错误"),
    UNAUTHORIZED(40100, "未登录或登录已过期"),
    FORBIDDEN(40300, "无权限"),
    NOT_FOUND(40400, "资源不存在"),

    INTERNAL_ERROR(50000, "服务端异常");

    private final int code;
    private final String message;

    CommonErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    @Override
    public int code() {
        return code;
    }

    @Override
    public String message() {
        return message;
    }
}

