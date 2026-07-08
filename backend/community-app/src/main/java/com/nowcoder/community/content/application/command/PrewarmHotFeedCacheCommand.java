package com.nowcoder.community.content.application.command;

import org.springframework.util.StringUtils;

import java.util.UUID;

public record PrewarmHotFeedCacheCommand(
        String scope,
        UUID boardId,
        int limit,
        String reason
) {

    public PrewarmHotFeedCacheCommand normalized() {
        return new PrewarmHotFeedCacheCommand(trim(scope), boardId, limit, trim(reason));
    }

    private static String trim(String value) {
        return StringUtils.hasText(value) ? value.trim() : "";
    }
}
