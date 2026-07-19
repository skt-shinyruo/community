package com.nowcoder.community.common.idempotency;

import com.nowcoder.community.common.exception.ErrorCode;
import com.nowcoder.community.common.exception.ErrorKind;

public enum IdempotencyErrorCode implements ErrorCode {

    IDEMPOTENCY_IN_PROGRESS(409, "请求处理中，请稍后重试", ErrorKind.CONFLICT),
    IDEMPOTENCY_OUTCOME_INDETERMINATE(409, "请求结果不确定，请查询业务状态", ErrorKind.CONFLICT),
    IDEMPOTENCY_STORE_UNAVAILABLE(503, "幂等存储不可用，请稍后重试", ErrorKind.UNAVAILABLE);

    private final int code;
    private final String message;
    private final ErrorKind kind;

    IdempotencyErrorCode(int code, String message, ErrorKind kind) {
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
