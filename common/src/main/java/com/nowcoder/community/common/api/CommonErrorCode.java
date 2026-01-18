package com.nowcoder.community.common.api;

public enum CommonErrorCode implements ErrorCode {

    OK(0, "OK"),

    INVALID_ARGUMENT(400, "参数错误"),
    UNAUTHORIZED(401, "未登录或登录已失效"),
    FORBIDDEN(403, "无权限访问"),
    NOT_FOUND(404, "资源不存在"),
    TOO_MANY_REQUESTS(429, "请求过于频繁"),

    SERVICE_UNAVAILABLE(503, "服务不可用"),
    INTERNAL_ERROR(500, "服务端异常");

    private final int code;
    private final String message;

    CommonErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    @Override
    public int getCode() {
        return code;
    }

    @Override
    public String getMessage() {
        return message;
    }
}
