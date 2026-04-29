package com.nowcoder.community.growth.api.model;

import java.time.Instant;
import java.util.UUID;

public record GrowthLikeTaskProgressRequest(String sourceEventId, UUID actorUserId, UUID entityUserId, Instant createTime) {
}
