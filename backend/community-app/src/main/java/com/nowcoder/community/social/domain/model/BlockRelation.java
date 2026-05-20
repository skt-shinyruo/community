package com.nowcoder.community.social.domain.model;

import java.util.UUID;

public record BlockRelation(UUID blockerUserId, UUID blockedUserId, long version) {

    public BlockRelation(UUID blockerUserId, UUID blockedUserId) {
        this(blockerUserId, blockedUserId, 0L);
    }
}
