package com.nowcoder.community.common.api;

/**
 * analytics 域错误码（统计/指标）。
 *
 * <p>约定：HTTP status 表达“错误类别”，Result.code 表达“业务细分”。</p>
 */
public enum AnalyticsErrorCode implements ErrorCode {

    RANGE_INVALID(16001, "时间范围不合法", 400),
    METRIC_NOT_SUPPORTED(16002, "指标类型不支持", 400),

    STORAGE_UNAVAILABLE(16003, "统计存储不可用", 503),
    INTERNAL_ERROR(16004, "统计服务异常", 500);

    private final int code;
    private final String message;
    private final int httpStatus;

    AnalyticsErrorCode(int code, String message, int httpStatus) {
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

