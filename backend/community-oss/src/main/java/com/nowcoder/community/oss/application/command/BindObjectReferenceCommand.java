package com.nowcoder.community.oss.application.command;

import java.time.Instant;
import java.util.UUID;

public record BindObjectReferenceCommand(
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
}
