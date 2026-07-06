package com.nowcoder.community.content.application.command;

import java.time.Instant;
import java.util.UUID;

public record RecordPostViewCommand(
        UUID postId,
        String viewerKey,
        Instant viewedAt
) {
}
