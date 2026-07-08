package com.nowcoder.community.social.domain.event;

import java.time.Instant;
import java.util.UUID;

public record BlockRelationChangedDomainEvent(
        UUID blockerUserId,
        UUID blockedUserId,
        boolean blocked,
        Instant occurredAt,
        long version
) {
}
