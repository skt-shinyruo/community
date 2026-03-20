package com.nowcoder.community.user.exception;

import com.nowcoder.community.common.exception.ErrorCode;

/**
 * user 域错误码（用户资料/头像/管理等）。
 *
 * <p>约定：HTTP status 表达“错误类别”，Result.code 表达“业务细分”。</p>
 */
public enum UserErrorCode implements ErrorCode {

    USER_NOT_FOUND(11001, "用户不存在", 404),
    USER_ALREADY_EXISTS(11002, "用户已存在", 409),
    EMAIL_ALREADY_EXISTS(11003, "邮箱已被注册", 409),

    AVATAR_FILE_INVALID(11004, "头像文件不合法", 400),
    AVATAR_SAVE_FAILED(11005, "保存头像失败", 500),
    STORAGE_UNAVAILABLE(11006, "用户存储不可用", 503),

    INTERNAL_ERROR(11007, "用户服务异常", 500);

    private final int code;
    private final String message;
    private final int httpStatus;

    UserErrorCode(int code, String message, int httpStatus) {
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
