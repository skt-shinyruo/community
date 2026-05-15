package com.nowcoder.community.user.api.model;

import java.util.UUID;

public record UserLikeRewardRequest(String sourceEventId, UUID actorUserId, UUID entityUserId) {
}
