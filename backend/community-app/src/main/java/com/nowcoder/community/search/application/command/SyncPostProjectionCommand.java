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
        Instant createTime,
        Double score
) {

    public SyncPostProjectionCommand {
        tags = tags == null ? List.of() : List.copyOf(tags);
    }
}
