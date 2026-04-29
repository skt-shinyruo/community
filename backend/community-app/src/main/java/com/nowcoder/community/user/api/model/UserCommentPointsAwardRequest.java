package com.nowcoder.community.user.api.model;

import java.util.UUID;

public record UserCommentPointsAwardRequest(UUID commentId, UUID userId) {
}
