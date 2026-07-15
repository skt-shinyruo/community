package com.nowcoder.community.user.exception;

import com.nowcoder.community.common.exception.ErrorCode;
import com.nowcoder.community.common.exception.ErrorKind;

/**
 * user 域错误码（用户资料/头像/管理等）。
 *
 * <p>约定：ErrorKind 表达稳定类别，Web adapter 映射 HTTP status。</p>
 */
public enum UserErrorCode implements ErrorCode {

    USER_NOT_FOUND(11001, "用户不存在", ErrorKind.NOT_FOUND),
    USER_ALREADY_EXISTS(11002, "用户已存在", ErrorKind.CONFLICT),
    EMAIL_ALREADY_EXISTS(11003, "邮箱已被注册", ErrorKind.CONFLICT),

    AVATAR_FILE_INVALID(11004, "头像文件不合法", ErrorKind.INVALID_INPUT),
    AVATAR_SAVE_FAILED(11005, "保存头像失败", ErrorKind.INTERNAL),
    STORAGE_UNAVAILABLE(11006, "用户存储不可用", ErrorKind.UNAVAILABLE),

    INTERNAL_ERROR(11007, "用户服务异常", ErrorKind.INTERNAL);

    private final int code;
    private final String message;
    private final ErrorKind kind;

    UserErrorCode(int code, String message, ErrorKind kind) {
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
