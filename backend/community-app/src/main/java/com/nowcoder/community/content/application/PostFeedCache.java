package com.nowcoder.community.content.application;

import com.nowcoder.community.content.application.result.HotFeedDegradationSignalResult;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface PostFeedCache {

    List<UUID> readGlobalHotIds(String cursor, int size);

    List<UUID> readBoardHotIds(UUID boardId, String cursor, int size);

    void upsertGlobalHot(UUID postId, double score, String rankVersion);

    default void upsertGlobalHot(UUID postId, double score, String rankVersion, long sourceVersion) {
        upsertGlobalHot(postId, score, rankVersion);
    }

    default void upsertGlobalHot(
            UUID postId,
            double score,
            String rankVersion,
            long aggregateVersion,
            long scoreVersion
    ) {
        upsertGlobalHot(postId, score, rankVersion, aggregateVersion);
    }

    void upsertBoardHot(UUID boardId, UUID postId, double score, String rankVersion);

    default void upsertBoardHot(UUID boardId, UUID postId, double score, String rankVersion, long sourceVersion) {
        upsertBoardHot(boardId, postId, score, rankVersion);
    }

    default void upsertBoardHot(
            UUID boardId,
            UUID postId,
            double score,
            String rankVersion,
            long aggregateVersion,
            long scoreVersion
    ) {
        upsertBoardHot(boardId, postId, score, rankVersion, aggregateVersion);
    }

    void writeRankVersion(String rankVersion);

    String readRankVersion();

    long countGlobalHot();

    long countBoardHot(UUID boardId);

    HotFeedDegradationSignalResult readDegradationSignal();

    HotFeedDegradationSignalResult writeDegradationSignal(boolean degraded, String reason);

    Instant readLastPrewarmAt(String scope, UUID boardId);

    void writeLastPrewarmAt(String scope, UUID boardId, Instant prewarmAt);

    /**
     * Removes the post from the global hot feed and, when {@code boardId} is null,
     * from all board hot feeds as well.
     */
    void remove(UUID postId, UUID boardId);

    default void remove(UUID postId, UUID boardId, long minimumVersion) {
        remove(postId, boardId);
    }

    /**
     * Fences the deleted post from global, payload-board, and current board feeds for the replay window.
     */
    void terminalRemove(UUID postId, UUID boardId);

    default void terminalRemove(UUID postId, UUID boardId, long minimumVersion) {
        terminalRemove(postId, boardId);
    }
}
