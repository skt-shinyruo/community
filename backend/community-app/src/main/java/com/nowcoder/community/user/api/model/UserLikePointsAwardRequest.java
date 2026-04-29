package com.nowcoder.community.user.api.model;

import java.util.UUID;

public record UserLikePointsAwardRequest(String sourceEventId, UUID actorUserId, UUID entityUserId) {
}
