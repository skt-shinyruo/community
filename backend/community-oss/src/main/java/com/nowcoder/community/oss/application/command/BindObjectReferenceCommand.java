package com.nowcoder.community.oss.application.command;

import java.time.Instant;
import java.util.UUID;

public record BindObjectReferenceCommand(
        UUID referenceId,
        UUID objectId,
        UUID versionId,
        String subjectService,
        String subjectDomain,
        String subjectType,
        String subjectId,
        String referenceRole,
        Instant retainUntil,
        String actorId
) {

    public BindObjectReferenceCommand(
            UUID objectId,
            UUID versionId,
            String subjectService,
            String subjectDomain,
            String subjectType,
            String subjectId,
            String referenceRole,
            Instant retainUntil,
            String actorId
    ) {
        this(null, objectId, versionId, subjectService, subjectDomain, subjectType, subjectId,
                referenceRole, retainUntil, actorId);
    }
}
