package com.nowcoder.community.im.core.api;

public enum CommonErrorCode implements ErrorCode {

    OK(0, "OK", 200),

    INVALID_ARGUMENT(400, "参数错误", 400),
    UNAUTHORIZED(401, "未登录或登录已失效", 401),
    FORBIDDEN(403, "无权限访问", 403),
    NOT_FOUND(404, "资源不存在", 404),
    TOO_MANY_REQUESTS(429, "请求过于频繁", 429),

    SERVICE_UNAVAILABLE(503, "服务不可用", 503),
    INTERNAL_ERROR(500, "服务端异常", 500);

    private final int code;
    private final String message;
    private final int httpStatus;

    CommonErrorCode(int code, String message, int httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }

    @Override
    public int getCode() {
        return code;
    }

    @Override
    public String getMessage() {
        return message;
    }

    @Override
    public int getHttpStatus() {
        return httpStatus;
    }
}

