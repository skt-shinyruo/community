package com.nowcoder.community.social.contracts.event;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.UUID;

public record LikePayload(
        UUID actorUserId,
        int entityType,
        UUID entityId,
        UUID entityUserId,
        UUID postId,
        String relationKey,
        @JsonInclude(JsonInclude.Include.NON_NULL) UUID relationInstanceId,
        @JsonInclude(JsonInclude.Include.NON_NULL) Long relationVersion
) {
}
