package com.nowcoder.community.content.contracts.event;

import java.io.Serializable;
import java.util.UUID;

public record PostScorePayload(
        UUID postId,
        long aggregateVersion,
        long scoreVersion,
        double score
) implements Serializable {

    public PostScorePayload {
        if (postId == null) {
            throw new IllegalArgumentException("postId must not be null");
        }
        if (aggregateVersion <= 0L) {
            throw new IllegalArgumentException("aggregateVersion must be positive");
        }
        if (scoreVersion <= 0L) {
            throw new IllegalArgumentException("scoreVersion must be positive");
        }
    }
}
