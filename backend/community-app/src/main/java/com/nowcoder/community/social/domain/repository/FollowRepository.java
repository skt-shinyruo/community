package com.nowcoder.community.social.domain.repository;

import com.nowcoder.community.social.domain.model.FollowRelation;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface FollowRepository {

    boolean follow(UUID userId, int entityType, UUID entityId, long followTimeMillis);

    boolean unfollow(UUID userId, int entityType, UUID entityId);

    boolean hasFollowed(UUID userId, int entityType, UUID entityId);

    Map<UUID, Boolean> followedStatusesBatch(UUID userId, int entityType, List<UUID> entityIds);

    long countFollowees(UUID userId, int entityType);

    long countFollowers(int entityType, UUID entityId);

    long countFolloweesExcludingBlocked(UUID userId, int entityType, BlockRepository blockRepository);

    long countFollowersExcludingBlocked(int entityType, UUID entityId, BlockRepository blockRepository);

    List<UUID> listFolloweeIdsExcludingBlocked(
            UUID userId,
            int entityType,
            BlockRepository blockRepository,
            int limit
    );

    List<FollowRelation> listFolloweesAfterExcludingBlocked(
            UUID userId,
            int entityType,
            BlockRepository blockRepository,
            Instant beforeTime,
            UUID beforeTargetId,
            int limit
    );

    List<FollowRelation> listFollowersAfterExcludingBlocked(
            int entityType,
            UUID entityId,
            BlockRepository blockRepository,
            Instant beforeTime,
            UUID beforeTargetId,
            int limit
    );
}
