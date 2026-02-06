package com.nowcoder.community.common.api;

/**
 * message 域错误码（私信/通知等）。
 *
 * <p>约定：HTTP status 表达“错误类别”，Result.code 表达“业务细分”。</p>
 */
public enum MessageErrorCode implements ErrorCode {

    CONVERSATION_NOT_FOUND(14001, "会话不存在", 404),

    MESSAGE_FORBIDDEN(14002, "无权限发送消息", 403),
    MESSAGE_CONFLICT(14003, "消息状态冲突", 409),

    INTERNAL_ERROR(14004, "消息服务异常", 500);

    private final int code;
    private final String message;
    private final int httpStatus;

    MessageErrorCode(int code, String message, int httpStatus) {
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

