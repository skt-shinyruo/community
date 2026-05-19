package com.nowcoder.community.im.common;

/**
 * IM Kafka topic constants.
 *
 * <p>Topics are cross-service stable contracts and must live in the contracts module.</p>
 */
public final class ImTopics {

    private ImTopics() {
    }

    public static final String COMMAND_PRIVATE_TEXT = "im.command.private-text";
    public static final String COMMAND_ROOM_TEXT = "im.command.room-text";

    public static final String EVENT_PRIVATE_PERSISTED = "im.event.private-persisted";
    public static final String EVENT_ROOM_PERSISTED = "im.event.room-persisted";
    public static final String EVENT_PRIVATE_COMMITTED = "im.event.private-committed";
    public static final String EVENT_ROOM_COMMITTED = "im.event.room-committed";
    public static final String EVENT_PRIVATE_REJECTED = "im.event.private-rejected";
    public static final String EVENT_ROOM_REJECTED = "im.event.room-rejected";

    public static final String EVENT_ROOM_MEMBER_CHANGED = "im.event.room-member-changed";
    public static final String EVENT_USER_MESSAGING_POLICY_CHANGED = "im.event.user-messaging-policy-changed";
    public static final String EVENT_USER_BLOCK_RELATION_CHANGED = "im.event.user-block-relation-changed";
}
