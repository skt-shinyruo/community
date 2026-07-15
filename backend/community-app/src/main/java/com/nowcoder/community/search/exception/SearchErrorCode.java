package com.nowcoder.community.search.exception;

import com.nowcoder.community.common.exception.ErrorCode;
import com.nowcoder.community.common.exception.ErrorKind;

/**
 * search 域错误码（搜索/索引等）。
 *
 * <p>约定：ErrorKind 表达稳定类别，Web adapter 映射 HTTP status。</p>
 */
public enum SearchErrorCode implements ErrorCode {

    INDEX_UNAVAILABLE(15001, "搜索索引不可用", ErrorKind.UNAVAILABLE),
    INTERNAL_ERROR(15003, "搜索服务异常", ErrorKind.INTERNAL);

    private final int code;
    private final String message;
    private final ErrorKind kind;

    SearchErrorCode(int code, String message, ErrorKind kind) {
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
