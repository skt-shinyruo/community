package com.nowcoder.community.growth.exception;

import com.nowcoder.community.common.exception.ErrorCode;
import com.nowcoder.community.common.exception.ErrorKind;

public enum GrowthErrorCode implements ErrorCode {

    INVALID_REQUEST(16001, "成长中心请求参数错误", ErrorKind.INVALID_INPUT),
    TARGET_USER_NOT_FOUND(16002, "目标用户不存在", ErrorKind.NOT_FOUND);

    private final int code;
    private final String message;
    private final ErrorKind kind;

    GrowthErrorCode(int code, String message, ErrorKind kind) {
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
