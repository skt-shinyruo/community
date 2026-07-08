package com.nowcoder.community.ops.application.command;

import org.springframework.util.StringUtils;

import java.util.UUID;

public record PrewarmHotCacheCommand(
        UUID actorUserId,
        String scope,
        UUID boardId,
        int limit,
        String reason
) {

    public PrewarmHotCacheCommand normalized() {
        return new PrewarmHotCacheCommand(
                actorUserId,
                StringUtils.hasText(scope) ? scope.trim() : "global",
                boardId,
                limit,
                StringUtils.hasText(reason) ? reason.trim() : ""
        );
    }
}
