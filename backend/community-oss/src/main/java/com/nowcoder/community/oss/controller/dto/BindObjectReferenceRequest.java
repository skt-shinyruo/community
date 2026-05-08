package com.nowcoder.community.oss.controller.dto;

import java.time.Instant;

public record BindObjectReferenceRequest(
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
