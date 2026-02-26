package com.nowcoder.community.social.api.event;

/**
 * social-service 作为生产方的事件类型定义。
 */
public final class SocialEventTypes {

    private SocialEventTypes() {
    }

    public static final String LIKE_CREATED = "LikeCreated";
    public static final String LIKE_REMOVED = "LikeRemoved";

    public static final String FOLLOW_CREATED = "FollowCreated";

    public static final String BLOCK_RELATION_CHANGED = "BlockRelationChanged";
}

