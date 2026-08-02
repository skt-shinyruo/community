package com.nowcoder.community.content.application;

import java.util.UUID;

public interface HotFeedProjectionGuard {

    /**
     * Compatibility entry point for legacy post-only callers. It never infers a lane from an event id.
     */
    @Deprecated
    default ProjectionAttempt tryBegin(
            UUID postId,
            String sourceEventId,
            long sourceVersion,
            boolean terminalDeletion
    ) {
        return tryBegin(postId, sourceEventId, sourceVersion, PostProjectionVersionLane.POST, terminalDeletion);
    }

    ProjectionAttempt tryBegin(
            UUID postId,
            String sourceEventId,
            long sourceVersion,
            PostProjectionVersionLane sourceVersionLane,
            boolean terminalDeletion
    );

    boolean isCurrent(ProjectionAttempt attempt);

    void commit(ProjectionAttempt attempt);

    void abort(ProjectionAttempt attempt);

    record ProjectionAttempt(
            UUID postId,
            String sourceEventId,
            long sourceVersion,
            PostProjectionVersionLane sourceVersionLane,
            boolean terminalDeletion,
            String token,
            boolean accepted
    ) {

        public static ProjectionAttempt accepted(
                UUID postId,
                String sourceEventId,
                long sourceVersion,
                PostProjectionVersionLane sourceVersionLane,
                boolean terminalDeletion,
                String token
        ) {
            return new ProjectionAttempt(
                    postId,
                    sourceEventId,
                    sourceVersion,
                    sourceVersionLane,
                    terminalDeletion,
                    token,
                    true
            );
        }

        @Deprecated
        public static ProjectionAttempt accepted(
                UUID postId,
                String sourceEventId,
                long sourceVersion,
                boolean terminalDeletion,
                String token
        ) {
            return accepted(
                    postId,
                    sourceEventId,
                    sourceVersion,
                    PostProjectionVersionLane.POST,
                    terminalDeletion,
                    token
            );
        }

        public static ProjectionAttempt rejected(
                UUID postId,
                String sourceEventId,
                long sourceVersion,
                PostProjectionVersionLane sourceVersionLane,
                boolean terminalDeletion
        ) {
            return new ProjectionAttempt(
                    postId,
                    sourceEventId,
                    sourceVersion,
                    sourceVersionLane,
                    terminalDeletion,
                    "",
                    false
            );
        }

        @Deprecated
        public static ProjectionAttempt rejected(
                UUID postId,
                String sourceEventId,
                long sourceVersion,
                boolean terminalDeletion
        ) {
            return rejected(
                    postId,
                    sourceEventId,
                    sourceVersion,
                    PostProjectionVersionLane.POST,
                    terminalDeletion
            );
        }
    }
}
