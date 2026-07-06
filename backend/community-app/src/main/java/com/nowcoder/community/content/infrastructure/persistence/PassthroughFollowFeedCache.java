package com.nowcoder.community.content.infrastructure.persistence;

import com.nowcoder.community.content.application.FollowFeedCache;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

@Repository
@ConditionalOnMissingBean(FollowFeedCache.class)
public class PassthroughFollowFeedCache implements FollowFeedCache {

    @Override
    public FollowFeedPageSlice getOrLoadPage(UUID userId, String cursor, int size, Supplier<FollowFeedPageSlice> loader) {
        if (userId == null) {
            return new FollowFeedPageSlice(List.of(), null, null);
        }
        return sanitize(loader == null ? null : loader.get());
    }

    private FollowFeedPageSlice sanitize(FollowFeedPageSlice loaded) {
        if (loaded == null) {
            return new FollowFeedPageSlice(List.of(), null, null);
        }
        return new FollowFeedPageSlice(
                loaded.ids() == null ? List.of() : loaded.ids().stream()
                .filter(id -> id != null)
                .distinct()
                .toList(),
                loaded.anchorCreateTime(),
                loaded.anchorPostId()
        );
    }
}
