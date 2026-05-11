package com.nowcoder.community.search.exception;

import com.nowcoder.community.common.exception.ErrorCode;

/**
 * search 域错误码（搜索/索引等）。
 *
 * <p>约定：HTTP status 表达“错误类别”，Result.code 表达“业务细分”。</p>
 */
public enum SearchErrorCode implements ErrorCode {

    INDEX_UNAVAILABLE(15001, "搜索索引不可用", 503),
    INTERNAL_ERROR(15003, "搜索服务异常", 500);

    private final int code;
    private final String message;
    private final int httpStatus;

    SearchErrorCode(int code, String message, int httpStatus) {
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
