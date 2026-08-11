package com.nowcoder.community.content.application;

import com.nowcoder.community.content.application.result.HotFeedDegradationSignalResult;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

public interface PostFeedCache {

    HotProjectionPage readGlobalHotProjection(String cursor, int size);

    HotProjectionPage readBoardHotProjection(UUID boardId, String cursor, int size);

    void upsertGlobalHot(
            HotProjectionEntry entry,
            String rankVersion,
            long aggregateVersion,
            long scoreVersion
    );

    void upsertBoardHot(
            UUID boardId,
            HotProjectionEntry entry,
            String rankVersion,
            long aggregateVersion,
            long scoreVersion
    );

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

    record HotProjectionPage(List<HotProjectionEntry> entries, long epoch, boolean hasNext) {

        public HotProjectionPage {
            entries = entries == null ? List.of() : List.copyOf(entries);
            epoch = Math.max(0L, epoch);
        }
    }

    record HotProjectionEntry(UUID postId, int type, double score, Date createTime) {

        public HotProjectionEntry {
            createTime = createTime == null ? null : new Date(createTime.getTime());
        }

        @Override
        public Date createTime() {
            return createTime == null ? null : new Date(createTime.getTime());
        }
    }
}
