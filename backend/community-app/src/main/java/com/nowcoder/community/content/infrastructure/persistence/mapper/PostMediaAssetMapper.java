package com.nowcoder.community.content.infrastructure.persistence.mapper;

import com.nowcoder.community.content.infrastructure.persistence.dataobject.PostMediaAssetDataObject;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Date;
import java.util.List;
import java.util.UUID;

@Mapper
public interface PostMediaAssetMapper {

    int insert(PostMediaAssetDataObject row);

    PostMediaAssetDataObject selectById(@Param("id") UUID id);

    List<PostMediaAssetDataObject> selectByIds(@Param("ids") List<UUID> ids);

    List<PostMediaAssetDataObject> selectByPostId(@Param("postId") UUID postId);

    int markUploaded(
            @Param("id") UUID id,
            @Param("versionId") UUID versionId,
            @Param("publicUrl") String publicUrl,
            @Param("updateTime") Date updateTime
    );

    int claimUploadCompletion(
            @Param("id") UUID id,
            @Param("actorUserId") UUID actorUserId,
            @Param("operationVersion") long operationVersion,
            @Param("updateTime") Date updateTime
    );

    int markObjectCompleted(
            @Param("id") UUID id,
            @Param("operationVersion") long operationVersion,
            @Param("versionId") UUID versionId,
            @Param("publicUrl") String publicUrl,
            @Param("contentType") String contentType,
            @Param("contentLength") long contentLength,
            @Param("updateTime") Date updateTime
    );

    int markUploadCompleted(
            @Param("id") UUID id,
            @Param("operationVersion") long operationVersion,
            @Param("updateTime") Date updateTime
    );

    int markUploadFailed(
            @Param("id") UUID id,
            @Param("operationVersion") long operationVersion,
            @Param("failureReason") String failureReason,
            @Param("updateTime") Date updateTime
    );

    int resetStaleUploadCompletion(
            @Param("id") UUID id,
            @Param("operationVersion") long operationVersion,
            @Param("staleBefore") Date staleBefore,
            @Param("resetAt") Date resetAt
    );

    int recordUploadRecoveryFailure(
            @Param("id") UUID id,
            @Param("operationVersion") long operationVersion,
            @Param("staleBefore") Date staleBefore,
            @Param("failureReason") String failureReason,
            @Param("updateTime") Date updateTime
    );

    List<PostMediaAssetDataObject> listStaleCompleting(
            @Param("updatedBefore") Date updatedBefore,
            @Param("limit") int limit
    );

    int markDraftDeleted(
            @Param("id") UUID id,
            @Param("updateTime") Date updateTime
    );

    int bindToPost(
            @Param("id") UUID id,
            @Param("postId") UUID postId,
            @Param("ossReferenceId") UUID ossReferenceId,
            @Param("videoState") String videoState,
            @Param("updateTime") Date updateTime
    );

    int releaseRemovedFromPost(
            @Param("postId") UUID postId,
            @Param("keepIds") List<UUID> keepIds,
            @Param("updateTime") Date updateTime
    );

    int requestBind(
            @Param("id") UUID id,
            @Param("postId") UUID postId,
            @Param("ossReferenceId") UUID ossReferenceId,
            @Param("videoState") String videoState,
            @Param("updateTime") Date updateTime
    );

    int markBound(
            @Param("id") UUID id,
            @Param("operationVersion") long operationVersion,
            @Param("updateTime") Date updateTime
    );

    int requestRelease(
            @Param("id") UUID id,
            @Param("updateTime") Date updateTime
    );

    int requestBindRepair(
            @Param("id") UUID id,
            @Param("updateTime") Date updateTime
    );

    int requestReleaseRepair(
            @Param("id") UUID id,
            @Param("updateTime") Date updateTime
    );

    int markReleased(
            @Param("id") UUID id,
            @Param("operationVersion") long operationVersion,
            @Param("updateTime") Date updateTime
    );

    List<PostMediaAssetDataObject> listPending(@Param("limit") int limit);

    List<PostMediaAssetDataObject> scanReferenceStatesAfter(
            @Param("afterAssetId") UUID afterAssetId,
            @Param("limit") int limit
    );
}
