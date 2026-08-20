package com.nowcoder.community.social.contracts.event;

import java.time.Instant;
import java.util.UUID;

public record FollowPayload(
        UUID actorUserId,
        int entityType,
        UUID entityId,
        UUID entityUserId,
        Instant createTime
) {
}
