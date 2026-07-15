package com.nowcoder.community.oss.controller;

import com.nowcoder.community.oss.application.ObjectAccessApplicationService;
import com.nowcoder.community.oss.application.ObjectLifecycleApplicationService;
import com.nowcoder.community.oss.application.ObjectPermissionApplicationService;
import com.nowcoder.community.oss.application.ObjectQueryApplicationService;
import com.nowcoder.community.oss.application.ObjectUploadApplicationService;
import com.nowcoder.community.oss.application.command.CreateSignedUrlCommand;
import com.nowcoder.community.oss.application.command.CompleteObjectUploadCommand;
import com.nowcoder.community.oss.application.command.DeleteObjectCommand;
import com.nowcoder.community.oss.application.command.GrantObjectAccessCommand;
import com.nowcoder.community.oss.application.command.ObjectUploadContent;
import com.nowcoder.community.oss.application.command.PrepareObjectUploadCommand;
import com.nowcoder.community.oss.application.command.RevokeObjectAccessCommand;
import com.nowcoder.community.oss.application.result.ObjectAccessDecisionResult;
import com.nowcoder.community.oss.application.result.ObjectLifecycleResult;
import com.nowcoder.community.oss.application.result.ObjectMetadataResult;
import com.nowcoder.community.oss.application.result.ObjectSignedUrlResult;
import com.nowcoder.community.oss.application.result.ObjectUploadSessionResult;
import com.nowcoder.community.oss.controller.dto.GrantObjectAccessRequest;
import com.nowcoder.community.oss.controller.dto.PrepareUploadSessionRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

@RestController
public class OssObjectController {

    private final ObjectUploadApplicationService uploadApplicationService;
    private final ObjectQueryApplicationService queryApplicationService;
    private final ObjectAccessApplicationService accessApplicationService;
    private final ObjectPermissionApplicationService permissionApplicationService;
    private final ObjectLifecycleApplicationService lifecycleApplicationService;

    public OssObjectController(
            ObjectUploadApplicationService uploadApplicationService,
            ObjectQueryApplicationService queryApplicationService,
            ObjectAccessApplicationService accessApplicationService,
            ObjectPermissionApplicationService permissionApplicationService,
            ObjectLifecycleApplicationService lifecycleApplicationService
    ) {
        this.uploadApplicationService = uploadApplicationService;
        this.queryApplicationService = queryApplicationService;
        this.accessApplicationService = accessApplicationService;
        this.permissionApplicationService = permissionApplicationService;
        this.lifecycleApplicationService = lifecycleApplicationService;
    }

    @PostMapping("/api/oss/objects/upload-sessions")
    public ObjectUploadSessionResult prepareUpload(@RequestBody PrepareUploadSessionRequest request) {
        return uploadApplicationService.prepareUpload(new PrepareObjectUploadCommand(
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

    @PostMapping(value = "/api/oss/objects/{objectId}/complete", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ObjectMetadataResult completeUpload(
            @PathVariable UUID objectId,
            @RequestParam UUID sessionId,
            @RequestParam UUID versionId,
            @RequestParam(required = false) String checksumSha256,
            @RequestParam("file") MultipartFile file
    ) {
        return uploadApplicationService.completeUpload(new CompleteObjectUploadCommand(
                sessionId,
                objectId,
                versionId,
                new ObjectUploadContent(
                        () -> openUploadStream(file),
                        file == null ? "application/octet-stream" : file.getContentType(),
                        file == null ? 0 : file.getSize(),
                        checksumSha256
                )
        ));
    }

    @GetMapping("/api/oss/objects/{objectId}")
    public ObjectMetadataResult getMetadata(@PathVariable UUID objectId) {
        return queryApplicationService.getMetadata(objectId);
    }

    @GetMapping("/api/oss/objects/{objectId}/signed-url")
    public ObjectSignedUrlResult createSignedUrl(
            @PathVariable UUID objectId,
            @RequestParam(name = "versionId", required = false) UUID versionId,
            @RequestParam(name = "ttlSeconds", required = false, defaultValue = "300") long ttlSeconds
    ) {
        return accessApplicationService.createSignedDownloadUrl(new CreateSignedUrlCommand(
                objectId,
                versionId,
                ttlSeconds,
                ""
        ));
    }

    @PostMapping("/api/oss/objects/{objectId}/grants")
    public ObjectAccessDecisionResult grantAccess(
            @PathVariable UUID objectId,
            @RequestBody GrantObjectAccessRequest request
    ) {
        return permissionApplicationService.grantAccess(new GrantObjectAccessCommand(
                objectId,
                parseUuid(request.versionId()),
                request.principalType(),
                request.principalValue(),
                request.permission(),
                request.expiresAt(),
                request.actorId()
        ));
    }

    @DeleteMapping("/api/oss/objects/{objectId}/grants/{grantId}")
    public ObjectAccessDecisionResult revokeAccess(
            @PathVariable UUID objectId,
            @PathVariable UUID grantId,
            @RequestParam(name = "actorId", required = false, defaultValue = "") String actorId
    ) {
        return permissionApplicationService.revokeAccess(new RevokeObjectAccessCommand(objectId, grantId, actorId));
    }

    @DeleteMapping("/api/oss/objects/{objectId}")
    public ObjectLifecycleResult deleteObject(
            @PathVariable UUID objectId,
            @RequestParam(name = "actorId", required = false, defaultValue = "") String actorId
    ) {
        return lifecycleApplicationService.deleteObject(new DeleteObjectCommand(objectId, actorId));
    }

    private InputStream openUploadStream(MultipartFile file) {
        try {
            return file == null ? InputStream.nullInputStream() : file.getInputStream();
        } catch (IOException e) {
            throw new IllegalStateException("failed to read upload file", e);
        }
    }

    private UUID parseUuid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return UUID.fromString(value.trim());
    }
}
