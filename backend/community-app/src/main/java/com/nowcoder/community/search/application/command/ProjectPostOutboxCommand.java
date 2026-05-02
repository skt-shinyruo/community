package com.nowcoder.community.search.application.command;

import java.util.UUID;

public record ProjectPostOutboxCommand(
        UUID postId,
        String sourceEventId,
        String sourceEventType
) {
}
