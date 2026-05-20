package com.nowcoder.community.social.api.model;

import java.util.UUID;

public record SocialBlockRelationView(UUID blockerUserId, UUID blockedUserId, long version) {

    public SocialBlockRelationView(UUID blockerUserId, UUID blockedUserId) {
        this(blockerUserId, blockedUserId, 0L);
    }
}
