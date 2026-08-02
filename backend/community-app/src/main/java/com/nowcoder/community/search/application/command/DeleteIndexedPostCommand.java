package com.nowcoder.community.search.application.command;

import java.util.UUID;

public record DeleteIndexedPostCommand(UUID postId, long aggregateVersion) {

    public DeleteIndexedPostCommand {
        if (aggregateVersion <= 0L) {
            throw new IllegalArgumentException("post projection aggregateVersion must be positive");
        }
    }
}
