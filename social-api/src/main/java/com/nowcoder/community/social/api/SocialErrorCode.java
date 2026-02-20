package com.nowcoder.community.social.api;

import com.nowcoder.community.common.api.ErrorCode;

/**
 * social 域错误码（点赞/关注/拉黑等关系）。
 *
 * <p>约定：HTTP status 表达“错误类别”，Result.code 表达“业务细分”。</p>
 */
public enum SocialErrorCode implements ErrorCode {

    CANNOT_FOLLOW_SELF(13001, "不能关注自己", 400),
    CANNOT_BLOCK_SELF(13002, "不能拉黑自己", 400),

    LIKE_CONFLICT(13003, "点赞状态冲突", 409),
    FOLLOW_CONFLICT(13004, "关注状态冲突", 409),
    BLOCK_CONFLICT(13005, "拉黑状态冲突", 409),

    INTERNAL_ERROR(13006, "社交服务异常", 500);

    private final int code;
    private final String message;
    private final int httpStatus;

    SocialErrorCode(int code, String message, int httpStatus) {
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
