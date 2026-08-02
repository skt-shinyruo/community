package com.nowcoder.community.content.application;

import com.nowcoder.community.content.application.result.PostSummaryResult;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface PostSummaryCache {

    Map<UUID, PostSummaryResult> getAll(List<UUID> postIds);

    void putAll(List<PostSummaryResult> summaries);

    default void putVersioned(List<VersionedSummary> summaries) {
        if (summaries == null || summaries.isEmpty()) {
            return;
        }
        putAll(summaries.stream().map(VersionedSummary::summary).toList());
    }

    void evictAll(List<UUID> postIds);

    default void evictAll(List<UUID> postIds, long minimumVersion) {
        evictAll(postIds);
    }

    default void evictAll(List<UUID> postIds, long minimumAggregateVersion, long minimumScoreVersion) {
        evictAll(postIds, minimumAggregateVersion);
    }

    void terminalEvict(UUID postId);

    default void terminalEvict(UUID postId, long minimumVersion) {
        terminalEvict(postId);
    }

    default void terminalEvict(UUID postId, long minimumAggregateVersion, long minimumScoreVersion) {
        terminalEvict(postId, minimumAggregateVersion);
    }

    record VersionedSummary(PostSummaryResult summary, long aggregateVersion, long scoreVersion) {

        public VersionedSummary(PostSummaryResult summary, long sourceVersion) {
            this(summary, sourceVersion, 0L);
        }

        public long sourceVersion() {
            return aggregateVersion;
        }
    }
}
