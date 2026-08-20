package com.nowcoder.community.drive.application.port;

import com.nowcoder.community.drive.application.command.DriveUploadContent;

import java.time.Instant;
import java.util.UUID;

public interface DriveObjectStoragePort {

    PreparedObject prepareUpload(PrepareObject command);

    void completeUpload(CompleteObject command);

    UploadCancellation cancelUpload(UUID sessionId, UUID objectId, UUID versionId);

    ObjectMetadata getMetadata(UUID objectId);

    SignedDownloadUrl createDownloadUrl(UUID objectId, long ttlSeconds);

    void deleteObject(UUID objectId, String actorId);

    record PrepareObject(
            UUID requestId,
            String usage,
            String ownerService,
            String ownerDomain,
            String ownerType,
            String ownerId,
            String visibility,
            String fileName,
            String contentType,
            long contentLength,
            String checksumSha256,
            String actorId
    ) {
    }

    record PreparedObject(UUID sessionId, UUID objectId, UUID versionId, Instant expiresAt) {
    }

    record CompleteObject(
            UUID sessionId,
            UUID objectId,
            UUID versionId,
            String fileName,
            String contentType,
            long contentLength,
            String checksumSha256,
            DriveUploadContent content
    ) {
    }

    record UploadCancellation(boolean completed, boolean cancelled) {
    }

    record ObjectMetadata(
            UUID objectId,
            UUID currentVersionId,
            String status
    ) {
    }

    record SignedDownloadUrl(String url, Instant expiresAt) {
    }
}
