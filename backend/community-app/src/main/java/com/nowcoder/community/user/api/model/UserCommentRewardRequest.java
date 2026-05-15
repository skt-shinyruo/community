package com.nowcoder.community.user.api.model;

import java.util.UUID;

public record UserCommentRewardRequest(UUID commentId, UUID userId) {
}
