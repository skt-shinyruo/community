package com.nowcoder.community.growth.domain.model;

import java.util.Objects;
import java.util.UUID;

/**
 * Durable ordering state for the task contribution of one stable like relation.
 */
public record LikeTaskLifecycleState(
        UUID recipientUserId,
        String relationKey,
        UUID relationInstanceId,
        long sourceVersion,
        boolean active,
        String sourceEventId
) {

    public LikeTaskLifecycleState {
        Objects.requireNonNull(recipientUserId, "recipientUserId must not be null");
        relationKey = requireText(relationKey, "relationKey");
        if (sourceVersion <= 0L) {
            throw new IllegalArgumentException("sourceVersion must be positive");
        }
        sourceEventId = requireText(sourceEventId, "sourceEventId");
    }

    public static Transition decide(LikeTaskLifecycleState current, LikeTaskLifecycleState incoming) {
        Objects.requireNonNull(incoming, "incoming must not be null");
        if (current == null) {
            return incoming.active ? Transition.ACTIVATED : Transition.DEACTIVATED;
        }
        requireSameRelation(current, incoming);
        if (current.relationInstanceId != null && incoming.relationInstanceId == null) {
            // Legacy events cannot prove that they belong to the known lifecycle.
            return Transition.IGNORED;
        }
        if (incoming.sourceVersion < current.sourceVersion) {
            return Transition.IGNORED;
        }
        if (incoming.sourceVersion == current.sourceVersion) {
            boolean distinctKnownLifecycle = current.relationInstanceId != null
                    && incoming.relationInstanceId != null
                    && !current.relationInstanceId.equals(incoming.relationInstanceId);
            if (distinctKnownLifecycle) {
                if (!incoming.active) {
                    return Transition.IGNORED;
                }
                return current.active ? Transition.REPLACED : Transition.ACTIVATED;
            }
            return current.active && !incoming.active
                    ? Transition.DEACTIVATED
                    : Transition.IGNORED;
        }
        if (current.active == incoming.active) {
            boolean replaced = incoming.active
                    && !Objects.equals(current.relationInstanceId, incoming.relationInstanceId);
            return replaced ? Transition.REPLACED : Transition.ADVANCED;
        }
        return incoming.active ? Transition.ACTIVATED : Transition.DEACTIVATED;
    }

    private static void requireSameRelation(LikeTaskLifecycleState current, LikeTaskLifecycleState incoming) {
        if (!current.recipientUserId.equals(incoming.recipientUserId)
                || !current.relationKey.equals(incoming.relationKey)) {
            throw new IllegalArgumentException("like task lifecycle states belong to different relations");
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
        REPLACED,
        ADVANCED,
        IGNORED
    }
}
