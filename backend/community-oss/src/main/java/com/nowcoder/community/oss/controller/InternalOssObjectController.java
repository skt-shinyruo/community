package com.nowcoder.community.oss.controller;

import com.nowcoder.community.oss.application.ObjectAccessApplicationService;
import com.nowcoder.community.oss.application.ObjectLifecycleApplicationService;
import com.nowcoder.community.oss.application.ObjectQueryApplicationService;
import com.nowcoder.community.oss.application.ObjectReferenceApplicationService;
import com.nowcoder.community.oss.application.ObjectUploadApplicationService;
import com.nowcoder.community.oss.application.command.BindObjectReferenceCommand;
import com.nowcoder.community.oss.application.command.CompleteObjectUploadCommand;
import com.nowcoder.community.oss.application.command.CreateSignedUrlCommand;
import com.nowcoder.community.oss.application.command.DeleteObjectCommand;
import com.nowcoder.community.oss.application.command.ObjectUploadContent;
import com.nowcoder.community.oss.application.command.PrepareObjectUploadCommand;
import com.nowcoder.community.oss.application.command.ReleaseObjectReferenceCommand;
import com.nowcoder.community.oss.application.result.ObjectMetadataResult;
import com.nowcoder.community.oss.application.result.ObjectLifecycleResult;
import com.nowcoder.community.oss.application.result.ObjectReferenceResult;
import com.nowcoder.community.oss.application.result.ObjectSignedUrlResult;
import com.nowcoder.community.oss.application.result.ObjectUploadSessionResult;
import com.nowcoder.community.oss.controller.dto.BindObjectReferenceRequest;
import com.nowcoder.community.oss.controller.dto.InternalPrepareUploadSessionRequest;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

@RestController
public class InternalOssObjectController {

    private final ObjectReferenceApplicationService referenceApplicationService;
    private final ObjectUploadApplicationService uploadApplicationService;
    private final ObjectQueryApplicationService queryApplicationService;
    private final ObjectAccessApplicationService accessApplicationService;
    private final ObjectLifecycleApplicationService lifecycleApplicationService;

    public InternalOssObjectController(
            ObjectUploadApplicationService uploadApplicationService,
            ObjectQueryApplicationService queryApplicationService,
            ObjectAccessApplicationService accessApplicationService,
            ObjectReferenceApplicationService referenceApplicationService,
            ObjectLifecycleApplicationService lifecycleApplicationService
    ) {
        this.uploadApplicationService = uploadApplicationService;
        this.queryApplicationService = queryApplicationService;
        this.accessApplicationService = accessApplicationService;
        this.referenceApplicationService = referenceApplicationService;
        this.lifecycleApplicationService = lifecycleApplicationService;
    }

    @PostMapping("/internal/oss/upload-sessions")
    public ObjectUploadSessionResult prepareUpload(
            @RequestBody InternalPrepareUploadSessionRequest request,
            Authentication authentication
    ) {
        String serviceSubject = requireServiceSubject(authentication);
        return uploadApplicationService.prepareInternalUpload(serviceSubject, new PrepareObjectUploadCommand(
                request.requestId(),
                request.usage(),
                request.ownerService(),
                request.ownerDomain(),
                request.ownerType(),
                request.ownerId(),
                request.visibility(),
                request.fileName(),
                request.contentType(),
                request.contentLength(),
                request.checksumSha256(),
                request.actorId()
        ));
    }

    @PostMapping(
            value = "/internal/oss/upload-sessions/{sessionId}/complete",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ObjectMetadataResult completeUpload(
            @PathVariable UUID sessionId,
            @RequestParam UUID objectId,
            @RequestParam UUID versionId,
            @RequestParam(required = false) String checksumSha256,
            @RequestParam("file") MultipartFile file,
            Authentication authentication
    ) {
        String serviceSubject = requireServiceSubject(authentication);
        return uploadApplicationService.completeInternalUpload(serviceSubject, new CompleteObjectUploadCommand(
                sessionId,
                objectId,
                versionId,
                new ObjectUploadContent(
                        () -> openUploadStream(file),
                        file == null ? "application/octet-stream" : file.getContentType(),
                        file == null ? 0 : file.getSize(),
                        checksumSha256
                ),
                "internal-upload-placeholder"
        ));
    }

    @GetMapping("/internal/oss/objects/{objectId}")
    public ObjectMetadataResult getMetadata(
            @PathVariable UUID objectId,
            Authentication authentication
    ) {
        return queryApplicationService.getInternalMetadata(
                objectId,
                requireServiceSubject(authentication));
    }

    @GetMapping("/internal/oss/objects/{objectId}/signed-url")
    public ObjectSignedUrlResult createSignedUrl(
            @PathVariable UUID objectId,
            @RequestParam(required = false) UUID versionId,
            @RequestParam(defaultValue = "300") long ttlSeconds,
            Authentication authentication
    ) {
        return accessApplicationService.createInternalSignedDownloadUrl(
                new CreateSignedUrlCommand(objectId, versionId, ttlSeconds, null),
                requireServiceSubject(authentication));
    }

    @DeleteMapping("/internal/oss/objects/{objectId}")
    public ObjectLifecycleResult deleteObject(
            @PathVariable UUID objectId,
            @RequestParam(name = "actorId", required = false, defaultValue = "") String actorId,
            Authentication authentication
    ) {
        return lifecycleApplicationService.deleteInternalObject(
                new DeleteObjectCommand(objectId, actorId),
                requireServiceSubject(authentication));
    }

    @PostMapping("/internal/oss/objects/{objectId}/references")
    public ObjectReferenceResult bindReference(
            @PathVariable UUID objectId,
            @RequestBody BindObjectReferenceRequest request,
            Authentication authentication
    ) {
        String serviceSubject = requireServiceSubject(authentication);
        return referenceApplicationService.bindInternalReference(serviceSubject, new BindObjectReferenceCommand(
                parseUuid(request.referenceId()),
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
            @RequestParam(name = "actorId", required = false, defaultValue = "") String actorId,
            Authentication authentication
    ) {
        String serviceSubject = requireServiceSubject(authentication);
        return referenceApplicationService.releaseInternalReference(
                serviceSubject,
                new ReleaseObjectReferenceCommand(objectId, referenceId, actorId));
    }

    @GetMapping("/internal/oss/objects/{objectId}/references/{referenceId}")
    public ObjectReferenceResult getReference(
            @PathVariable UUID objectId,
            @PathVariable UUID referenceId,
            Authentication authentication
    ) {
        return referenceApplicationService.getInternalReference(
                objectId,
                referenceId,
                requireServiceSubject(authentication));
    }

    private UUID parseUuid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return UUID.fromString(value.trim());
    }

    private InputStream openUploadStream(MultipartFile file) {
        try {
            return file == null ? InputStream.nullInputStream() : file.getInputStream();
        } catch (IOException e) {
            throw new IllegalStateException("failed to read upload file", e);
        }
    }

    private String requireServiceSubject(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || authentication.getName() == null || authentication.getName().isBlank()) {
            throw new IllegalArgumentException("authenticated service subject must not be blank");
        }
        return authentication.getName().trim();
    }
}
