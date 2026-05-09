package com.nowcoder.community.drive.application.port;

import com.nowcoder.community.drive.application.command.DriveUploadContent;

import java.time.Instant;
import java.util.UUID;

public interface DriveObjectStoragePort {

    PreparedObject prepareUpload(PrepareObject command);

    StoredObject completeUpload(CompleteObject command);

    SignedDownloadUrl createDownloadUrl(UUID objectId, long ttlSeconds);

    void deleteObject(UUID objectId, String actorId);

    record PrepareObject(
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

    record StoredObject(UUID objectId, UUID versionId, String publicUrl) {
    }

    record SignedDownloadUrl(String url, Instant expiresAt) {
    }
}
