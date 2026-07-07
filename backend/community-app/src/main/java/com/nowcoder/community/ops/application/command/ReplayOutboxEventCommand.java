package com.nowcoder.community.ops.application.command;

import java.util.UUID;

public record ReplayOutboxEventCommand(UUID actorUserId, UUID outboxId, String reason) {
    public String normalizedReason() {
        return reason == null || reason.isBlank() ? "" : reason.trim();
    }
}
