package com.nowcoder.community.user.api.action;

import com.nowcoder.community.user.api.model.UserModerationStateView;

import java.util.UUID;

public interface UserModerationActionApi {

    /**
     * Revalidates the actor from the owner-domain record and holds the role-management
     * fence for the caller's current transaction.
     */
    void assertActiveModerationActor(UUID actorUserId);

    UserModerationStateView applyModeration(ApplyModerationCommand command);

    record ApplyModerationCommand(
            UUID actorUserId,
            UUID targetUserId,
            String action,
            int durationSeconds
    ) {
    }
}
