package com.nowcoder.community.content.api;

import com.nowcoder.community.common.api.ErrorCode;

/**
 * content 域错误码（帖子/评论/内容治理等）。
 *
 * <p>约定：HTTP status 表达“错误类别”，Result.code 表达“业务细分”。</p>
 */
public enum ContentErrorCode implements ErrorCode {

    POST_NOT_FOUND(12001, "帖子不存在", 404),
    COMMENT_NOT_FOUND(12002, "评论不存在", 404),
    CATEGORY_NOT_FOUND(12003, "分类不存在", 404),
    TAG_NOT_FOUND(12004, "标签不存在", 404),

    BOOKMARK_CONFLICT(12005, "收藏状态冲突", 409),
    SUBSCRIPTION_CONFLICT(12006, "订阅状态冲突", 409),

    CONTENT_RENDER_FAILED(12007, "内容渲染失败", 500),
    INTERNAL_ERROR(12008, "内容服务异常", 500),

    /**
     * 本地读模型投影缺失（最终一致系统的兜底失败语义）。
     *
     * <p>说明：通常由投影冷启动/漏消费/滞后导致；上层可选择 bootstrap 回填或 fail-closed（503）。</p>
     */
    PROJECTION_MISSING(12009, "投影缺失", 503);

    private final int code;
    private final String message;
    private final int httpStatus;

    ContentErrorCode(int code, String message, int httpStatus) {
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
