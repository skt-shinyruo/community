package com.nowcoder.community.social.domain.model;

import java.time.Instant;
import java.util.UUID;

public record FollowRelation(UUID targetId, Instant followTime) {
}
