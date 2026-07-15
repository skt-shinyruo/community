package com.nowcoder.community.oss.client.model;

import java.time.Instant;

public record OssBindReferenceRequest(
        String referenceId,
        String versionId,
        String subjectService,
        String subjectDomain,
        String subjectType,
        String subjectId,
        String referenceRole,
        Instant retainUntil,
        String actorId
) {

    public OssBindReferenceRequest(
            String versionId,
            String subjectService,
            String subjectDomain,
            String subjectType,
            String subjectId,
            String referenceRole,
            Instant retainUntil,
            String actorId
    ) {
        this(null, versionId, subjectService, subjectDomain, subjectType, subjectId,
                referenceRole, retainUntil, actorId);
    }
}
