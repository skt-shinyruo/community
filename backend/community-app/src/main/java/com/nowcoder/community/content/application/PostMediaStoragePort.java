package com.nowcoder.community.content.application;

import com.nowcoder.community.content.application.result.PostMediaUploadSessionResult;
import com.nowcoder.community.content.domain.model.PostMediaAsset;

import java.util.UUID;

public interface PostMediaStoragePort {

    PostMediaUploadSessionResult prepareUpload(PostMediaAsset draft, String checksumSha256);

    UploadedPostMedia completeUpload(PostMediaAsset draft, UUID uploadSessionId, PostMediaUploadContent content);

    CanonicalPostMedia queryCanonicalMetadata(PostMediaAsset asset);

    void deleteDraftObject(PostMediaAsset asset, UUID actorUserId);

    UUID bindReference(PostMediaAsset asset, UUID postId, UUID requestedReferenceId, UUID actorUserId);

    default UUID bindReference(PostMediaAsset asset, UUID postId, UUID actorUserId) {
        return bindReference(asset, postId, null, actorUserId);
    }

    void releaseReference(PostMediaAsset asset, UUID actorUserId);

    record UploadedPostMedia(UUID versionId, String publicUrl, String contentType, long contentLength) {
    }

    record CanonicalPostMedia(
            CanonicalMetadataOutcome outcome,
            UUID objectId,
            UUID versionId,
            String publicUrl,
            String contentType,
            long contentLength,
            String checksumSha256
    ) {
    }

    enum CanonicalMetadataOutcome {
        FOUND,
        NOT_FOUND,
        UNKNOWN
    }
}
