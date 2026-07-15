package com.nowcoder.community.oss.controller.dto;

import java.time.Instant;

public record BindObjectReferenceRequest(
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

    public BindObjectReferenceRequest(
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
