package com.nowcoder.community.social.domain.model;

import java.util.UUID;

public record BlockRelation(UUID blockerUserId, UUID blockedUserId) {
}
