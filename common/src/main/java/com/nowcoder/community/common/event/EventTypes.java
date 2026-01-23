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
}
