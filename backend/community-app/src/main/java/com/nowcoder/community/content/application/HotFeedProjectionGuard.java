package com.nowcoder.community.content.application;

import java.util.UUID;

public interface HotFeedProjectionGuard {

    ProjectionAttempt tryBegin(UUID postId, String sourceEventId, long sourceVersion);

    boolean isCurrent(ProjectionAttempt attempt);

    void commit(ProjectionAttempt attempt);

    void abort(ProjectionAttempt attempt);

    record ProjectionAttempt(
            UUID postId,
            String sourceEventId,
            long sourceVersion,
            String token,
            boolean accepted
    ) {

        public static ProjectionAttempt accepted(UUID postId, String sourceEventId, long sourceVersion, String token) {
            return new ProjectionAttempt(postId, sourceEventId, sourceVersion, token, true);
        }

        public static ProjectionAttempt rejected(UUID postId, String sourceEventId, long sourceVersion) {
            return new ProjectionAttempt(postId, sourceEventId, sourceVersion, "", false);
        }
    }
}
