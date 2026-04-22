package com.nowcoder.community.im.common;

/**
 * IM Kafka topic constants (v1).
 *
 * <p>Topics are cross-service stable contracts and must live in the contracts module.</p>
 */
public final class ImTopics {

    private ImTopics() {
    }

    public static final String COMMAND_PRIVATE_TEXT_V1 = "im.command.private_text.v1";
    public static final String COMMAND_ROOM_TEXT_V1 = "im.command.room_text.v1";

    public static final String EVENT_PRIVATE_PERSISTED_V1 = "im.event.private_persisted.v1";
    public static final String EVENT_ROOM_PERSISTED_V1 = "im.event.room_persisted.v1";
    public static final String EVENT_PRIVATE_REJECTED_V1 = "im.event.private_rejected.v1";
    public static final String EVENT_ROOM_REJECTED_V1 = "im.event.room_rejected.v1";
    public static final String EVENT_ROOM_MEMBER_CHANGED_V1 = "im.event.room_member_changed.v1";
}
