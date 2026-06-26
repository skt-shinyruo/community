package com.nowcoder.community.notice.application.command;

import java.util.UUID;

public record CreateNoticeCommand(
        UUID toUserId,
        String topic,
        String contentJson,
        String sourceEventType,
        String sourceRelationKey
) {

    public CreateNoticeCommand(UUID toUserId, String topic, String contentJson) {
        this(toUserId, topic, contentJson, null, null);
    }
}
