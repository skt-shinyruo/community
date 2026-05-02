package com.nowcoder.community.social.domain.model;

import java.util.UUID;

public record LikeRelation(UUID actorUserId, int entityType, UUID entityId, UUID entityUserId) {
}
