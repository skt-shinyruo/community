package com.nowcoder.community.content.application;

import java.util.List;
import java.util.UUID;

public interface PostFeedCache {

    List<UUID> readGlobalHotIds(String cursor, int size);

    List<UUID> readBoardHotIds(UUID boardId, String cursor, int size);

    void upsertGlobalHot(UUID postId, double score, String rankVersion);

    void upsertBoardHot(UUID boardId, UUID postId, double score, String rankVersion);

    void writeRankVersion(String rankVersion);

    String readRankVersion();

    /**
     * Removes the post from the global hot feed and, when {@code boardId} is null,
     * from all board hot feeds as well.
     */
    void remove(UUID postId, UUID boardId);
}
