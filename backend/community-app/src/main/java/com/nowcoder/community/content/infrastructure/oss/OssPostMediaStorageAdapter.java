package com.nowcoder.community.content.infrastructure.oss;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.content.application.PostMediaUploadContent;
import com.nowcoder.community.content.application.PostMediaStoragePort;
import com.nowcoder.community.content.application.result.PostMediaUploadSessionResult;
import com.nowcoder.community.content.domain.model.PostMediaAsset;
import com.nowcoder.community.oss.client.CommunityOssClient;
import com.nowcoder.community.oss.client.model.OssBindReferenceRequest;
import com.nowcoder.community.oss.client.model.OssCompleteUploadRequest;
import com.nowcoder.community.oss.client.model.OssMetadataResponse;
import com.nowcoder.community.oss.client.model.OssReferenceResponse;
import com.nowcoder.community.oss.client.model.OssUploadSessionRequest;
import com.nowcoder.community.oss.client.model.OssUploadSessionResponse;
import org.springframework.stereotype.Component;

import java.util.UUID;

import static com.nowcoder.community.common.exception.CommonErrorCode.INTERNAL_ERROR;
import static com.nowcoder.community.common.exception.CommonErrorCode.INVALID_ARGUMENT;

@Component
public class OssPostMediaStorageAdapter implements PostMediaStoragePort {

    private static final String USAGE = "CONTENT_POST_MEDIA";
    private static final String OWNER_SERVICE = "community-app";
    private static final String OWNER_DOMAIN = "content";
    private static final String DRAFT_OWNER_TYPE = "post-media-draft";
    private static final String VISIBILITY_PUBLIC = "PUBLIC";
    private static final String UPLOAD_METHOD = "POST";
    private static final String FILE_FIELD = "file";
    private static final String UPLOAD_ID_FIELD = "uploadId";
    private static final String POST_REFERENCE_TYPE = "post";
    private static final String POST_REFERENCE_ROLE = "POST_MEDIA";
    private static final long MAX_BYTES = 100L * 1024 * 1024;
    private static final String MIME_TYPES = "image/png;image/jpeg;image/webp;image/gif;video/mp4;video/webm;application/pdf;application/zip";

    private final CommunityOssClient ossClient;

    public OssPostMediaStorageAdapter(CommunityOssClient ossClient) {
        this.ossClient = ossClient;
    }

    @Override
    public PostMediaUploadSessionResult prepareUpload(PostMediaAsset draft, String checksumSha256) {
        requireDraftIdentity(draft);
        OssUploadSessionResponse response = ossClient.prepareUpload(new OssUploadSessionRequest(
                USAGE,
                OWNER_SERVICE,
                OWNER_DOMAIN,
                DRAFT_OWNER_TYPE,
                draft.id().toString(),
                VISIBILITY_PUBLIC,
                draft.fileName(),
                draft.contentType(),
                draft.contentLength(),
                checksumSha256 == null ? "" : checksumSha256.trim(),
                draft.ownerUserId().toString()
        ));
        if (response == null || response.sessionId() == null || response.objectId() == null || response.versionId() == null) {
            throw new BusinessException(INTERNAL_ERROR, "签发媒体上传参数失败");
        }
        return new PostMediaUploadSessionResult(
                draft.id(),
                response.sessionId().toString(),
                "/api/posts/media/" + draft.id() + "/upload",
                UPLOAD_METHOD,
                FILE_FIELD,
                UPLOAD_ID_FIELD,
                MAX_BYTES,
                MIME_TYPES,
                response.expiresAt(),
                response.objectId(),
                response.versionId()
        );
    }

    @Override
    public UploadedPostMedia completeUpload(PostMediaAsset draft, UUID uploadSessionId, PostMediaUploadContent content) {
        if (draft == null || draft.ossObjectId() == null || draft.ossVersionId() == null || uploadSessionId == null || content == null) {
            throw new BusinessException(INVALID_ARGUMENT, "上传上下文非法");
        }
        OssMetadataResponse metadata;
        try {
            metadata = ossClient.completeProxyUpload(new OssCompleteUploadRequest(
                    uploadSessionId,
                    draft.ossObjectId(),
                    draft.ossVersionId(),
                    content::openStream,
                    draft.fileName(),
                    content.contentType(),
                    content.size(),
                    content.checksumSha256()
            ));
        } catch (RuntimeException e) {
            throw new BusinessException(INTERNAL_ERROR, "上传媒体失败", e);
        }
        if (metadata == null || metadata.currentVersionId() == null) {
            throw new BusinessException(INTERNAL_ERROR, "上传媒体失败");
        }
        return new UploadedPostMedia(
                metadata.currentVersionId(),
                metadata.publicUrl(),
                metadata.contentType(),
                metadata.contentLength()
        );
    }

    @Override
    public void deleteDraftObject(PostMediaAsset asset, UUID actorUserId) {
        if (asset == null || asset.ossObjectId() == null) {
            return;
        }
        ossClient.deleteObject(asset.ossObjectId(), actorUserId == null ? "" : actorUserId.toString());
    }

    @Override
    public UUID bindReference(PostMediaAsset asset, UUID postId, UUID actorUserId) {
        if (asset == null || asset.ossObjectId() == null || asset.ossVersionId() == null || postId == null || actorUserId == null) {
            throw new BusinessException(INVALID_ARGUMENT, "媒体绑定上下文非法");
        }
        OssReferenceResponse response = ossClient.bindObjectReference(asset.ossObjectId(), new OssBindReferenceRequest(
                asset.ossVersionId().toString(),
                OWNER_SERVICE,
                OWNER_DOMAIN,
                POST_REFERENCE_TYPE,
                postId.toString(),
                POST_REFERENCE_ROLE,
                null,
                actorUserId.toString()
        ));
        if (response == null || response.referenceId() == null) {
            throw new BusinessException(INTERNAL_ERROR, "绑定媒体引用失败");
        }
        return response.referenceId();
    }

    @Override
    public void releaseReference(PostMediaAsset asset, UUID actorUserId) {
        if (asset == null || asset.ossObjectId() == null || asset.ossReferenceId() == null) {
            return;
        }
        ossClient.releaseObjectReference(asset.ossObjectId(), asset.ossReferenceId(), actorUserId == null ? "" : actorUserId.toString());
    }

    private static void requireDraftIdentity(PostMediaAsset draft) {
        if (draft == null || draft.id() == null || draft.ownerUserId() == null) {
            throw new BusinessException(INVALID_ARGUMENT, "媒体草稿非法");
        }
    }
}
