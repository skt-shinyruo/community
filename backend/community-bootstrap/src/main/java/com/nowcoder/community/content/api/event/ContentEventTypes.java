package com.nowcoder.community.content.api.event;

/**
 * content-service 作为生产方的事件类型定义。
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

    // 处罚命令：由 content-service 发起，user-service 消费并执行（最终一致）
    public static final String MODERATION_COMMAND_REQUESTED = "ModerationCommandRequested";
}
