package com.nowcoder.community.growth.api.model;

import java.time.Instant;
import java.util.UUID;

public record GrowthCommentTaskProgressRequest(UUID commentId, UUID userId, Instant createTime) {
}
