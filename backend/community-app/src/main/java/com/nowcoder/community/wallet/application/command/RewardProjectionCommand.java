package com.nowcoder.community.wallet.application.command;

import java.util.UUID;

public record RewardProjectionCommand(
        UUID userId,
        int delta,
        String sourceId,
        String sourceType
) {
}
