package com.nowcoder.community.analytics.exception;

import com.nowcoder.community.common.exception.ErrorCode;
import com.nowcoder.community.common.exception.ErrorKind;

/**
 * analytics 域错误码（UV/DAU 等统计）。
 *
 * <p>约定：ErrorKind 表达稳定类别，Web adapter 映射 HTTP status。</p>
 */
public enum AnalyticsErrorCode implements ErrorCode {

    COUNTER_UNAVAILABLE(16001, "统计存储不可用", ErrorKind.UNAVAILABLE),
    INTERNAL_ERROR(16002, "统计服务异常", ErrorKind.INTERNAL),
    RANGE_INVALID(16003, "查询区间不合法", ErrorKind.INVALID_INPUT);

    private final int code;
    private final String message;
    private final ErrorKind kind;

    AnalyticsErrorCode(int code, String message, ErrorKind kind) {
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
