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

    public BlockRelationChangedDomainEvent(UUID blockerUserId, UUID blockedUserId, boolean blocked) {
        this(blockerUserId, blockedUserId, blocked, null, 0L);
    }

    public BlockRelationChangedDomainEvent(UUID blockerUserId, UUID blockedUserId, boolean blocked, long version) {
        this(blockerUserId, blockedUserId, blocked, null, version);
    }
}
