package com.nowcoder.community.content.domain.repository;

import com.nowcoder.community.content.domain.model.PostMediaAsset;
import com.nowcoder.community.content.domain.model.PostVideoState;

import java.util.Date;
import java.util.List;
import java.util.UUID;

public interface PostMediaAssetRepository {

    PostMediaAsset getRequired(UUID assetId);

    List<PostMediaAsset> listByIds(List<UUID> assetIds);

    List<PostMediaAsset> listByPostId(UUID postId);

    UUID createDraft(PostMediaAsset asset);

    void markUploaded(UUID assetId, UUID versionId, String publicUrl, Date updateTime);

    boolean claimUploadCompletion(UUID assetId, UUID actorUserId, long operationVersion, Date updateTime);

    boolean markObjectCompleted(
            UUID assetId,
            long operationVersion,
            UUID versionId,
            String publicUrl,
            String contentType,
            long contentLength,
            Date updateTime
    );

    boolean markUploadCompleted(UUID assetId, long operationVersion, Date updateTime);

    boolean markUploadFailed(UUID assetId, long operationVersion, String failureReason, Date updateTime);

    boolean resetStaleUploadCompletion(
            UUID assetId,
            long operationVersion,
            Date staleBefore,
            Date resetAt
    );

    boolean recordUploadRecoveryFailure(
            UUID assetId,
            long operationVersion,
            Date staleBefore,
            String failureReason,
            Date updateTime
    );

    List<PostMediaAsset> listStaleCompleting(Date updatedBefore, int limit);

    void markDraftDeleted(UUID assetId, Date updateTime);

    void bindToPost(UUID assetId, UUID postId, UUID ossReferenceId, PostVideoState videoState, Date updateTime);

    void releaseRemovedFromPost(UUID postId, List<UUID> keepIds, Date updateTime);

    long requestBind(
            UUID assetId,
            UUID postId,
            UUID ossReferenceId,
            PostVideoState videoState,
            Date updateTime
    );

    boolean markBound(UUID assetId, long operationVersion, Date updateTime);

    long requestRelease(UUID assetId, Date updateTime);

    long requestBindRepair(UUID assetId, Date updateTime);

    long requestReleaseRepair(UUID assetId, Date updateTime);

    boolean markReleased(UUID assetId, long operationVersion, Date updateTime);

    List<PostMediaAsset> listPending(int limit);

    List<PostMediaAsset> scanReferenceStatesAfter(UUID afterAssetId, int limit);
}
