package com.nowcoder.community.content.application;

import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

public interface FollowFeedCache {

    FollowFeedPageSlice getOrLoadPage(UUID userId, String cursor, int size, Supplier<FollowFeedPageSlice> loader);

    record FollowFeedPageSlice(List<UUID> ids, Date anchorCreateTime, UUID anchorPostId) {

        public FollowFeedPageSlice {
            ids = ids == null ? List.of() : new java.util.ArrayList<>(ids);
        }
    }
}
