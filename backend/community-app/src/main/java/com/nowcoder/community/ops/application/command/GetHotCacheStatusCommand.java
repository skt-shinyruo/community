package com.nowcoder.community.ops.application.command;

import org.springframework.util.StringUtils;

import java.util.UUID;

public record GetHotCacheStatusCommand(
        String scope,
        UUID boardId
) {

    public GetHotCacheStatusCommand normalized() {
        return new GetHotCacheStatusCommand(StringUtils.hasText(scope) ? scope.trim() : "global", boardId);
    }
}
