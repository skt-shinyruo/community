package com.nowcoder.community.social.exception;

import com.nowcoder.community.common.exception.ErrorCode;
import com.nowcoder.community.common.exception.ErrorKind;

/**
 * social 域错误码（点赞/关注/拉黑等关系）。
 *
 * <p>约定：ErrorKind 表达稳定类别，Web adapter 映射 HTTP status。</p>
 */
public enum SocialErrorCode implements ErrorCode {

    CANNOT_FOLLOW_SELF(13001, "不能关注自己", ErrorKind.INVALID_INPUT),
    CANNOT_BLOCK_SELF(13002, "不能拉黑自己", ErrorKind.INVALID_INPUT),

    LIKE_CONFLICT(13003, "点赞状态冲突", ErrorKind.CONFLICT),
    FOLLOW_CONFLICT(13004, "关注状态冲突", ErrorKind.CONFLICT),
    BLOCK_CONFLICT(13005, "拉黑状态冲突", ErrorKind.CONFLICT),

    INTERNAL_ERROR(13006, "社交服务异常", ErrorKind.INTERNAL);

    private final int code;
    private final String message;
    private final ErrorKind kind;

    SocialErrorCode(int code, String message, ErrorKind kind) {
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
