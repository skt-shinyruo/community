package com.nowcoder.community.social.domain.repository;

import com.nowcoder.community.social.domain.model.LikeTargetState;

import java.util.Optional;
import java.util.List;
import java.util.UUID;

public interface LikeTargetStateRepository {

    boolean insertActiveIfAbsent(int entityType, UUID entityId);

    Optional<LikeTargetState> findByTarget(int entityType, UUID entityId);

    LikeTargetState findForUpdate(int entityType, UUID entityId);

    boolean saveIfNewer(LikeTargetState state);

    List<LikeTargetState> scanDeletedTargetsWithLikesAfter(int entityType, UUID afterEntityId, int limit);
}
