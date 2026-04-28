package com.nowcoder.community.social.domain.event;

import java.time.Instant;
import java.util.UUID;

public record FollowCreatedDomainEvent(
        UUID actorUserId,
        int entityType,
        UUID entityId,
        UUID entityUserId,
        Instant createTime
) {
}
