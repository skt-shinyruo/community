package com.nowcoder.community.im.application;

import com.nowcoder.community.im.common.projection.ProjectionVersions;
import com.nowcoder.community.im.common.projection.UserMessagingPolicyEntry;
import com.nowcoder.community.user.api.model.UserModerationStateView;

import java.time.Instant;

/**
 * Maps the user owner moderation state to the shared IM policy entry so snapshot pages and the
 * synchronous private-message decision apply identical suspended / muted / canSendPrivate semantics.
 */
final class UserMessagingPolicyEntryAssembler {

    private UserMessagingPolicyEntryAssembler() {
    }

    static UserMessagingPolicyEntry assemble(UserModerationStateView state, Instant now) {
        if (state == null) {
            throw new IllegalStateException("user moderation state must not be null");
        }
        if (state.userId() == null) {
            throw new IllegalStateException("user moderation state userId must not be null");
        }
        long version = ProjectionVersions.requirePositive(state.version(), "version");
        boolean suspended = state.banUntil() != null && state.banUntil().isAfter(now);
        boolean muted = state.muteUntil() != null && state.muteUntil().isAfter(now);
        boolean canSendPrivate = !suspended && !muted;
        return new UserMessagingPolicyEntry(
                state.userId(),
                true,
                suspended,
                muted,
                toEpochMillis(state.muteUntil()),
                toEpochMillis(state.banUntil()),
                canSendPrivate,
                version,
                now.toEpochMilli()
        );
    }

    private static Long toEpochMillis(Instant instant) {
        return instant == null ? null : instant.toEpochMilli();
    }
}
