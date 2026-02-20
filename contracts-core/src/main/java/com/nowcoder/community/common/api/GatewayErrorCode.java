package com.nowcoder.community.common.api;

/**
 * gateway 域错误码（网关鉴权/限流/请求防护等）。
 *
 * <p>约定：HTTP status 表达“错误类别”，Result.code 表达“业务细分”。</p>
 */
public enum GatewayErrorCode implements ErrorCode {

    REQUEST_TOO_LARGE(17001, "请求体过大", 413),
    ORIGIN_NOT_ALLOWED(17002, "来源不被允许", 403),
    RATE_LIMITED(17003, "请求过于频繁", 429),
    ROUTE_NOT_FOUND(17005, "路由不存在", 404),
    BAD_GATEWAY(17006, "上游响应异常", 502),
    UPSTREAM_UNAVAILABLE(17007, "上游服务不可用", 503),
    GATEWAY_TIMEOUT(17008, "网关超时", 504),
    INTERNAL_ERROR(17004, "网关异常", 500);

    private final int code;
    private final String message;
    private final int httpStatus;

    GatewayErrorCode(int code, String message, int httpStatus) {
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
