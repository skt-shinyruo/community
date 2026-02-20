package com.nowcoder.community.user.api.event;

/**
 * user-service 作为生产方的事件类型定义。
 */
public final class UserEventTypes {

    private UserEventTypes() {
    }

    /**
     * 处罚状态变更：user-service 作为源（mute/ban/unmute/unban）。
     */
    public static final String MODERATION_STATUS_CHANGED = "ModerationStatusChanged";
}

