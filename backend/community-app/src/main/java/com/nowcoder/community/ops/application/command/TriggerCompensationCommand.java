package com.nowcoder.community.ops.application.command;

import org.springframework.util.StringUtils;

import java.util.UUID;

public record TriggerCompensationCommand(
        UUID actorUserId,
        String jobName,
        int limit,
        String reason
) {

    public TriggerCompensationCommand normalized() {
        return new TriggerCompensationCommand(actorUserId, trim(jobName), limit, trim(reason));
    }

    private static String trim(String value) {
        return StringUtils.hasText(value) ? value.trim() : "";
    }
}
