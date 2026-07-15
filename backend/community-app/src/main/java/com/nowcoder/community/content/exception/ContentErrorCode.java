package com.nowcoder.community.content.exception;

import com.nowcoder.community.common.exception.ErrorCode;
import com.nowcoder.community.common.exception.ErrorKind;

/**
 * content 域错误码（帖子/评论/内容治理等）。
 *
 * <p>约定：ErrorKind 表达稳定类别，Web adapter 映射 HTTP status。</p>
 */
public enum ContentErrorCode implements ErrorCode {

    POST_NOT_FOUND(12001, "帖子不存在", ErrorKind.NOT_FOUND),
    COMMENT_NOT_FOUND(12002, "评论不存在", ErrorKind.NOT_FOUND),
    CATEGORY_NOT_FOUND(12003, "分类不存在", ErrorKind.NOT_FOUND),
    TAG_NOT_FOUND(12004, "标签不存在", ErrorKind.NOT_FOUND),

    BOOKMARK_CONFLICT(12005, "收藏状态冲突", ErrorKind.CONFLICT),
    SUBSCRIPTION_CONFLICT(12006, "订阅状态冲突", ErrorKind.CONFLICT),
    REQUEST_REPLAY_CONFLICT(12009, "请求号与已有内容请求不一致", ErrorKind.CONFLICT),

    CONTENT_RENDER_FAILED(12007, "内容渲染失败", ErrorKind.INTERNAL),
    INTERNAL_ERROR(12008, "内容服务异常", ErrorKind.INTERNAL);

    private final int code;
    private final String message;
    private final ErrorKind kind;

    ContentErrorCode(int code, String message, ErrorKind kind) {
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
