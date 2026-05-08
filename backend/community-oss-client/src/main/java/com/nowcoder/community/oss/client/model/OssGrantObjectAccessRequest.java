package com.nowcoder.community.oss.client.model;

import java.time.Instant;

public record OssGrantObjectAccessRequest(
        String versionId,
        String principalType,
        String principalValue,
        String permission,
        Instant expiresAt,
        String actorId
) {
}
