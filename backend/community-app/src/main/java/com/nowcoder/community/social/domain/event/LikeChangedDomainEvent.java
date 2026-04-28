package com.nowcoder.community.social.domain.event;

import java.time.Instant;
import java.util.UUID;

public record LikeChangedDomainEvent(
        UUID actorUserId,
        int entityType,
        UUID entityId,
        UUID entityUserId,
        UUID postId,
        boolean liked,
        Instant createTime
) {
}
