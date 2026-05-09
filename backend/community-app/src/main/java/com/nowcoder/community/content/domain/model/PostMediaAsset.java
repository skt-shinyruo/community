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
        PostVideoState videoState,
        String publicUrl,
        String failureReason,
        Date createTime,
        Date updateTime
) {
}
