package com.nowcoder.community.growth.exception;

import com.nowcoder.community.common.exception.ErrorCode;

public enum GrowthErrorCode implements ErrorCode {

    INVALID_REQUEST(16001, "成长中心请求参数错误", 400),
    TARGET_USER_NOT_FOUND(16002, "目标用户不存在", 404);

    private final int code;
    private final String message;
    private final int httpStatus;

    GrowthErrorCode(int code, String message, int httpStatus) {
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
