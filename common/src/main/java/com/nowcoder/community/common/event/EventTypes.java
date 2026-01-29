package com.nowcoder.community.common.event;

public final class EventTypes {

    private EventTypes() {
    }

    public static final String POST_PUBLISHED = "PostPublished";
    public static final String POST_UPDATED = "PostUpdated";
    public static final String POST_DELETED = "PostDeleted";

    public static final String COMMENT_CREATED = "CommentCreated";

    public static final String LIKE_CREATED = "LikeCreated";
    public static final String FOLLOW_CREATED = "FollowCreated";

    public static final String MODERATION_ACTION_APPLIED = "ModerationActionApplied";

    // 处罚状态变更：user-service 作为源（mute/ban/unmute/unban）
    public static final String MODERATION_STATUS_CHANGED = "ModerationStatusChanged";

    // 处罚命令：由 content-service 发起，user-service 消费并执行（最终一致）
    public static final String MODERATION_COMMAND_REQUESTED = "ModerationCommandRequested";

    // 拉黑关系变更：social-service 作为源（block/unblock）
    public static final String BLOCK_RELATION_CHANGED = "BlockRelationChanged";
}
