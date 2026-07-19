package com.nowcoder.community.oss.controller.dto;

import java.time.Instant;

public record GrantObjectAccessRequest(
        String versionId,
        String principalType,
        String principalValue,
        String permission,
        Instant expiresAt
) {
}
