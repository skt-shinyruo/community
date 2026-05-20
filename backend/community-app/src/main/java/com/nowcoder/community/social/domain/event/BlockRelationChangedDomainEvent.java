package com.nowcoder.community.social.domain.event;

import java.util.UUID;

public record BlockRelationChangedDomainEvent(UUID blockerUserId, UUID blockedUserId, boolean blocked, long version) {

    public BlockRelationChangedDomainEvent(UUID blockerUserId, UUID blockedUserId, boolean blocked) {
        this(blockerUserId, blockedUserId, blocked, 0L);
    }
}
