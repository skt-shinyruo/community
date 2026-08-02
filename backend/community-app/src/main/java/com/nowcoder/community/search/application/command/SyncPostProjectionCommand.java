package com.nowcoder.community.search.application.command;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SyncPostProjectionCommand(
        UUID postId,
        UUID userId,
        UUID categoryId,
        List<String> tags,
        String title,
        String content,
        Integer type,
        Integer status,
        long aggregateVersion,
        long scoreVersion,
        Instant createTime,
        Double score
) {

    public SyncPostProjectionCommand {
        tags = tags == null ? List.of() : List.copyOf(tags);
        if (aggregateVersion <= 0L) {
            throw new IllegalArgumentException("post projection aggregateVersion must be positive");
        }
        if (scoreVersion < 0L) {
            throw new IllegalArgumentException("post projection scoreVersion must not be negative");
        }
    }
}
