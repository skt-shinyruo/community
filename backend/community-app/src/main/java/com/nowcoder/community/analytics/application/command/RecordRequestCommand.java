package com.nowcoder.community.analytics.application.command;

import java.util.UUID;

public record RecordRequestCommand(
        String ip,
        UUID userId,
        boolean recordUv,
        boolean recordDau
) {
}
