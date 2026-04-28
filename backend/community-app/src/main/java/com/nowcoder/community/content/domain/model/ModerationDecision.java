package com.nowcoder.community.content.domain.model;

import java.util.UUID;

public record ModerationDecision(
        UUID actorId,
        UUID reportId,
        String normalizedAction,
        String normalizedReason,
        int resolvedDurationSeconds
) {

    public boolean isReject() {
        return "reject".equals(normalizedAction);
    }

    public boolean isContentAction() {
        return "hide".equals(normalizedAction) || "delete".equals(normalizedAction);
    }

    public boolean isWarn() {
        return "warn".equals(normalizedAction);
    }

    public boolean isUserModerationAction() {
        return "mute".equals(normalizedAction) || "ban".equals(normalizedAction);
    }

    public Integer auditDurationSeconds() {
        return resolvedDurationSeconds == 0 ? null : resolvedDurationSeconds;
    }
}
