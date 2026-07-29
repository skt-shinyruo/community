package com.nowcoder.community.oss.client.model;

import java.util.UUID;

public record OssUploadCancellationResponse(
        UUID sessionId,
        UUID objectId,
        UUID versionId,
        String status,
        long claimVersion,
        boolean completed,
        boolean cancelled
) {
}
