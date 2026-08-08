package com.nowcoder.community.social.domain.model;

import java.util.UUID;

import static com.nowcoder.community.common.constants.EntityTypes.POST;

public record LikeRelation(
        UUID relationInstanceId,
        UUID actorUserId,
        int entityType,
        UUID entityId,
        UUID entityUserId,
        UUID postId
) {

    public LikeRelation(
            UUID relationInstanceId,
            UUID actorUserId,
            int entityType,
            UUID entityId,
            UUID entityUserId
    ) {
        this(
                relationInstanceId,
                actorUserId,
                entityType,
                entityId,
                entityUserId,
                entityType == POST ? entityId : null
        );
    }
}
