package com.nowcoder.community.content.application.command;

import java.util.UUID;

public record ProjectPostHotFeedCommand(
        String sourceEventId,
        String sourceEventType,
        UUID postId,
        UUID boardId,
        double signalWeight
) {
}
