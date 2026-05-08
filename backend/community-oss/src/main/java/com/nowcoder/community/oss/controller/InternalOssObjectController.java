package com.nowcoder.community.oss.controller;

import com.nowcoder.community.oss.application.ObjectReferenceApplicationService;
import com.nowcoder.community.oss.application.command.BindObjectReferenceCommand;
import com.nowcoder.community.oss.application.command.ReleaseObjectReferenceCommand;
import com.nowcoder.community.oss.application.result.ObjectReferenceResult;
import com.nowcoder.community.oss.controller.dto.BindObjectReferenceRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
public class InternalOssObjectController {

    private final ObjectReferenceApplicationService referenceApplicationService;

    public InternalOssObjectController(ObjectReferenceApplicationService referenceApplicationService) {
        this.referenceApplicationService = referenceApplicationService;
    }

    @PostMapping("/internal/oss/objects/{objectId}/references")
    public ObjectReferenceResult bindReference(
            @PathVariable UUID objectId,
            @RequestBody BindObjectReferenceRequest request
    ) {
        return referenceApplicationService.bindReference(new BindObjectReferenceCommand(
                objectId,
                parseUuid(request.versionId()),
                request.subjectService(),
                request.subjectDomain(),
                request.subjectType(),
                request.subjectId(),
                request.referenceRole(),
                request.retainUntil(),
                request.actorId()
        ));
    }

    @DeleteMapping("/internal/oss/objects/{objectId}/references/{referenceId}")
    public ObjectReferenceResult releaseReference(
            @PathVariable UUID objectId,
            @PathVariable UUID referenceId,
            @RequestParam(name = "actorId", required = false, defaultValue = "") String actorId
    ) {
        return referenceApplicationService.releaseReference(new ReleaseObjectReferenceCommand(objectId, referenceId, actorId));
    }

    private UUID parseUuid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return UUID.fromString(value.trim());
    }
}
