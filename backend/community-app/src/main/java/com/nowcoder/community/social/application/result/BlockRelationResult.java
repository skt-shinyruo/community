package com.nowcoder.community.social.application.result;

import java.util.UUID;

public record BlockRelationResult(UUID blockerUserId, UUID blockedUserId) {
}
