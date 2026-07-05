package com.nowcoder.community.notice.application.command;

import java.util.UUID;

public record CreateNoticeCommand(
        UUID toUserId,
        String noticeTopic,
        String contentJson,
        String sourceEventType,
        String sourceRelationKey
) {

    public CreateNoticeCommand(UUID toUserId, String noticeTopic, String contentJson) {
        this(toUserId, noticeTopic, contentJson, null, null);
    }
}
