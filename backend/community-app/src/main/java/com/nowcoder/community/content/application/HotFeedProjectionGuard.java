package com.nowcoder.community.content.application;

import java.util.UUID;

public interface HotFeedProjectionGuard {

    ProjectionAttempt tryBegin(UUID postId, String sourceEventId, long sourceVersion, boolean terminalDeletion);

    boolean isCurrent(ProjectionAttempt attempt);

    void commit(ProjectionAttempt attempt);

    void abort(ProjectionAttempt attempt);

    record ProjectionAttempt(
            UUID postId,
            String sourceEventId,
            long sourceVersion,
            boolean terminalDeletion,
            String token,
            boolean accepted
    ) {

        public static ProjectionAttempt accepted(
                UUID postId,
                String sourceEventId,
                long sourceVersion,
                boolean terminalDeletion,
                String token
        ) {
            return new ProjectionAttempt(postId, sourceEventId, sourceVersion, terminalDeletion, token, true);
        }

        public static ProjectionAttempt rejected(
                UUID postId,
                String sourceEventId,
                long sourceVersion,
                boolean terminalDeletion
        ) {
            return new ProjectionAttempt(postId, sourceEventId, sourceVersion, terminalDeletion, "", false);
        }
    }
}
