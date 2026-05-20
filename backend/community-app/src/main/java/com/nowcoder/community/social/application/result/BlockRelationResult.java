package com.nowcoder.community.social.application.result;

import java.util.UUID;

public record BlockRelationResult(UUID blockerUserId, UUID blockedUserId, long version) {

    public BlockRelationResult(UUID blockerUserId, UUID blockedUserId) {
        this(blockerUserId, blockedUserId, 0L);
    }
}
