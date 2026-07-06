package com.nowcoder.community.content.application.command;

import java.util.UUID;

public record ProjectPostHotFeedCommand(
        UUID postId,
        UUID boardId,
        double signalWeight,
        String sourceEventId,
        long sourceVersion
) {
}
