package com.nowcoder.community.common.exception;

public enum CommonErrorCode implements ErrorCode {

    INVALID_ARGUMENT(400, "参数错误", ErrorKind.INVALID_INPUT),
    UNAUTHORIZED(401, "未登录或登录已失效", ErrorKind.UNAUTHENTICATED),
    FORBIDDEN(403, "无权限访问", ErrorKind.FORBIDDEN),
    NOT_FOUND(404, "资源不存在", ErrorKind.NOT_FOUND),
    TOO_MANY_REQUESTS(429, "请求过于频繁", ErrorKind.THROTTLED),

    SERVICE_UNAVAILABLE(503, "服务不可用", ErrorKind.UNAVAILABLE),
    INTERNAL_ERROR(500, "服务端异常", ErrorKind.INTERNAL);

    private final int code;
    private final String message;
    private final ErrorKind kind;

    CommonErrorCode(int code, String message, ErrorKind kind) {
        this.code = code;
        this.message = message;
        this.kind = kind;
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
    public ErrorKind getKind() {
        return kind;
    }
}
