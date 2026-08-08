package com.nowcoder.community.notice.domain.model;

import java.util.Objects;
import java.util.UUID;

/**
 * Durable ordering state for the notice projection of one stable like relation.
 */
public record LikeNoticeProjectionState(
        UUID recipientUserId,
        String sourceRelationKey,
        UUID relationInstanceId,
        long sourceVersion,
        boolean active,
        String sourceEventId
) {

    public LikeNoticeProjectionState {
        Objects.requireNonNull(recipientUserId, "recipientUserId must not be null");
        sourceRelationKey = requireText(sourceRelationKey, "sourceRelationKey");
        if (sourceVersion <= 0L) {
            throw new IllegalArgumentException("sourceVersion must be positive");
        }
        sourceEventId = requireText(sourceEventId, "sourceEventId");
    }

    public static Transition decide(
            LikeNoticeProjectionState current,
            LikeNoticeProjectionState incoming
    ) {
        Objects.requireNonNull(incoming, "incoming must not be null");
        if (current == null) {
            return incoming.active ? Transition.ACTIVATED : Transition.DEACTIVATED;
        }
        requireSameRelation(current, incoming);
        if (incoming.sourceVersion < current.sourceVersion) {
            return Transition.IGNORED;
        }
        if (incoming.sourceVersion == current.sourceVersion) {
            boolean distinctKnownLifecycle = current.relationInstanceId != null
                    && incoming.relationInstanceId != null
                    && !current.relationInstanceId.equals(incoming.relationInstanceId);
            if (distinctKnownLifecycle) {
                // Legacy timestamp versions can tie across two lifecycles. The
                // active lifecycle wins so a removal for A cannot revoke B.
                return incoming.active ? Transition.ACTIVATED : Transition.IGNORED;
            }
            // Legacy producers used millisecond timestamps and could create/remove in
            // one tick. A removal wins that tie so an old notice cannot remain visible.
            return current.active && !incoming.active
                    ? Transition.DEACTIVATED
                    : Transition.IGNORED;
        }
        if (!Objects.equals(current.relationInstanceId, incoming.relationInstanceId)) {
            return incoming.active ? Transition.ACTIVATED : Transition.DEACTIVATED;
        }
        if (current.active == incoming.active) {
            return Transition.ADVANCED;
        }
        return incoming.active ? Transition.ACTIVATED : Transition.DEACTIVATED;
    }

    private static void requireSameRelation(
            LikeNoticeProjectionState current,
            LikeNoticeProjectionState incoming
    ) {
        if (!current.recipientUserId.equals(incoming.recipientUserId)
                || !current.sourceRelationKey.equals(incoming.sourceRelationKey)) {
            throw new IllegalArgumentException("like notice projection states belong to different relations");
        }
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }

    public enum Transition {
        ACTIVATED,
        DEACTIVATED,
        ADVANCED,
        IGNORED
    }
}
