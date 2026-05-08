package com.nowcoder.community.oss.client.model;

import java.time.Instant;

public record OssBindReferenceRequest(
        String versionId,
        String subjectService,
        String subjectDomain,
        String subjectType,
        String subjectId,
        String referenceRole,
        Instant retainUntil,
        String actorId
) {
}
