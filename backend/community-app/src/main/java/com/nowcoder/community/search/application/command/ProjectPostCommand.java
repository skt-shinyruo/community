package com.nowcoder.community.search.application.command;

import java.util.UUID;

public record ProjectPostCommand(
        UUID postId,
        String sourceEventId,
        long sourceVersion
) {
}
