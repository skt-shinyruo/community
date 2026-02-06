package com.nowcoder.community.common.api;

/**
 * search 域错误码（搜索与索引运维）。
 *
 * <p>约定：HTTP status 表达“错误类别”，Result.code 表达“业务细分”。</p>
 */
public enum SearchErrorCode implements ErrorCode {

    QUERY_INVALID(15001, "搜索参数错误", 400),

    INDEX_UNAVAILABLE(15002, "搜索索引不可用", 503),
    REINDEX_IN_PROGRESS(15003, "重建索引进行中", 409),

    INTERNAL_ERROR(15004, "搜索服务异常", 500);

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

