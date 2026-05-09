package com.nowcoder.community.content.application.port;

import com.nowcoder.community.content.application.PostMediaUploadContent;
import com.nowcoder.community.content.application.result.PostMediaUploadSessionResult;
import com.nowcoder.community.content.domain.model.PostMediaAsset;

import java.util.UUID;

public interface PostMediaStoragePort {

    PostMediaUploadSessionResult prepareUpload(PostMediaAsset draft, String checksumSha256);

    UploadedPostMedia completeUpload(PostMediaAsset draft, UUID uploadSessionId, PostMediaUploadContent content);

    UUID bindReference(PostMediaAsset asset, UUID postId, UUID actorUserId);

    void releaseReference(PostMediaAsset asset, UUID actorUserId);

    record UploadedPostMedia(UUID versionId, String publicUrl, String contentType, long contentLength) {
    }
}
