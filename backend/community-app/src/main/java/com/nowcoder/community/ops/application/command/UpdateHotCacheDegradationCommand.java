package com.nowcoder.community.ops.application.command;

import org.springframework.util.StringUtils;

import java.util.UUID;

public record UpdateHotCacheDegradationCommand(
        UUID actorUserId,
        boolean degraded,
        String reason
) {

    public UpdateHotCacheDegradationCommand normalized() {
        return new UpdateHotCacheDegradationCommand(
                actorUserId,
                degraded,
                StringUtils.hasText(reason) ? reason.trim() : ""
        );
    }
}
