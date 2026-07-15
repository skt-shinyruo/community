package com.nowcoder.community.content.domain.model;

import java.util.Date;
import java.util.UUID;

public record PostMediaAsset(
        UUID id,
        UUID ownerUserId,
        UUID postId,
        UUID ossObjectId,
        UUID ossVersionId,
        UUID ossReferenceId,
        UUID uploadSessionId,
        String fileName,
        String contentType,
        long contentLength,
        PostMediaKind mediaKind,
        PostMediaAssetLifecycle lifecycle,
        PostMediaUploadStatus uploadStatus,
        long uploadOperationVersion,
        Date uploadUpdatedAt,
        PostMediaReferenceStatus referenceStatus,
        long referenceOperationVersion,
        Date referenceUpdatedAt,
        PostVideoState videoState,
        String publicUrl,
        String failureReason,
        Date createTime,
        Date updateTime
) {

    public PostMediaAsset(
            UUID id,
            UUID ownerUserId,
            UUID postId,
            UUID ossObjectId,
            UUID ossVersionId,
            UUID ossReferenceId,
            UUID uploadSessionId,
            String fileName,
            String contentType,
            long contentLength,
            PostMediaKind mediaKind,
            PostMediaAssetLifecycle lifecycle,
            PostMediaReferenceStatus referenceStatus,
            long referenceOperationVersion,
            Date referenceUpdatedAt,
            PostVideoState videoState,
            String publicUrl,
            String failureReason,
            Date createTime,
            Date updateTime
    ) {
        this(
                id,
                ownerUserId,
                postId,
                ossObjectId,
                ossVersionId,
                ossReferenceId,
                uploadSessionId,
                fileName,
                contentType,
                contentLength,
                mediaKind,
                lifecycle,
                legacyUploadStatus(lifecycle),
                legacyUploadVersion(lifecycle),
                updateTime == null ? createTime : updateTime,
                referenceStatus,
                referenceOperationVersion,
                referenceUpdatedAt,
                videoState,
                publicUrl,
                failureReason,
                createTime,
                updateTime
        );
    }

    public PostMediaAsset(
            UUID id,
            UUID ownerUserId,
            UUID postId,
            UUID ossObjectId,
            UUID ossVersionId,
            UUID ossReferenceId,
            UUID uploadSessionId,
            String fileName,
            String contentType,
            long contentLength,
            PostMediaKind mediaKind,
            PostMediaAssetLifecycle lifecycle,
            PostVideoState videoState,
            String publicUrl,
            String failureReason,
            Date createTime,
            Date updateTime
    ) {
        this(
                id,
                ownerUserId,
                postId,
                ossObjectId,
                ossVersionId,
                ossReferenceId,
                uploadSessionId,
                fileName,
                contentType,
                contentLength,
                mediaKind,
                lifecycle,
                legacyUploadStatus(lifecycle),
                legacyUploadVersion(lifecycle),
                updateTime == null ? createTime : updateTime,
                legacyReferenceStatus(lifecycle, postId, ossReferenceId),
                legacyReferenceVersion(lifecycle, postId, ossReferenceId),
                updateTime == null ? createTime : updateTime,
                videoState,
                publicUrl,
                failureReason,
                createTime,
                updateTime
        );
    }

    private static PostMediaUploadStatus legacyUploadStatus(PostMediaAssetLifecycle lifecycle) {
        return lifecycle == PostMediaAssetLifecycle.DRAFT
                ? PostMediaUploadStatus.PREPARED
                : PostMediaUploadStatus.COMPLETED;
    }

    private static long legacyUploadVersion(PostMediaAssetLifecycle lifecycle) {
        return lifecycle == PostMediaAssetLifecycle.DRAFT ? 0L : 1L;
    }

    private static PostMediaReferenceStatus legacyReferenceStatus(
            PostMediaAssetLifecycle lifecycle,
            UUID postId,
            UUID ossReferenceId
    ) {
        if (lifecycle == PostMediaAssetLifecycle.RELEASED || lifecycle == PostMediaAssetLifecycle.DELETED) {
            return PostMediaReferenceStatus.RELEASED;
        }
        if (lifecycle == PostMediaAssetLifecycle.BOUND || ossReferenceId != null) {
            return PostMediaReferenceStatus.BOUND;
        }
        if (postId != null) {
            return PostMediaReferenceStatus.BIND_PENDING;
        }
        return PostMediaReferenceStatus.UNBOUND;
    }

    private static long legacyReferenceVersion(
            PostMediaAssetLifecycle lifecycle,
            UUID postId,
            UUID ossReferenceId
    ) {
        return legacyReferenceStatus(lifecycle, postId, ossReferenceId) == PostMediaReferenceStatus.UNBOUND ? 0L : 1L;
    }
}
