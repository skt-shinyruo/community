package com.nowcoder.community.social.application.result;

import java.time.Instant;
import java.util.UUID;

public record FollowRelationResult(UUID targetId, Instant followTime) {
}
