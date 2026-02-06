package com.nowcoder.community.common.api;

/**
 * gateway 域错误码（仅表示网关自身产生/收敛的错误）。
 *
 * <p>约定：HTTP status 表达“错误类别”，Result.code 表达“业务细分”。</p>
 */
public enum GatewayErrorCode implements ErrorCode {

    ROUTE_NOT_FOUND(17001, "路由不存在", 404),

    UPSTREAM_UNAVAILABLE(17002, "上游服务不可用", 503),
    BAD_GATEWAY(17003, "上游服务错误", 502),
    GATEWAY_TIMEOUT(17004, "网关超时", 504),

    REQUEST_TOO_LARGE(17005, "请求体过大", 413),
    INTERNAL_ERROR(17006, "网关异常", 500);

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

