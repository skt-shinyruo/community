package com.nowcoder.community.oss.application.result;

import java.time.Instant;
import java.util.UUID;

public record ObjectReferenceResult(
        UUID referenceId,
        UUID objectId,
        UUID versionId,
        String subjectService,
        String subjectDomain,
        String subjectType,
        String subjectId,
        String referenceRole,
        String status,
        Instant retainUntil,
        Instant createdAt,
        Instant releasedAt
) {
}
