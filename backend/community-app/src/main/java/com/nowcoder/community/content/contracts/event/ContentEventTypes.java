package com.nowcoder.community.content.contracts.event;

/**
 * content 模块作为生产方的事件类型定义。
 */
public final class ContentEventTypes {

    private ContentEventTypes() {
    }

    public static final String POST_PUBLISHED = "PostPublished";
    public static final String POST_UPDATED = "PostUpdated";
    public static final String POST_DELETED = "PostDeleted";

    public static final String COMMENT_CREATED = "CommentCreated";
    public static final String COMMENT_DELETED = "CommentDeleted";

    public static final String MODERATION_ACTION_APPLIED = "ModerationActionApplied";

    // 处罚命令：由 content 模块发起，user 模块消费并执行（最终一致）
    public static final String MODERATION_COMMAND_REQUESTED = "ModerationCommandRequested";
}
