package com.nowcoder.community.content.infrastructure.persistence;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.id.UuidV7Generator;
import com.nowcoder.community.content.domain.model.PostMediaAsset;
import com.nowcoder.community.content.domain.model.PostMediaAssetLifecycle;
import com.nowcoder.community.content.domain.model.PostMediaKind;
import com.nowcoder.community.content.domain.model.PostMediaReferenceStatus;
import com.nowcoder.community.content.domain.model.PostMediaUploadStatus;
import com.nowcoder.community.content.domain.model.PostVideoState;
import com.nowcoder.community.content.domain.repository.PostMediaAssetRepository;
import com.nowcoder.community.content.infrastructure.persistence.dataobject.PostMediaAssetDataObject;
import com.nowcoder.community.content.infrastructure.persistence.mapper.PostMediaAssetMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.List;
import java.util.UUID;

import static com.nowcoder.community.common.exception.CommonErrorCode.NOT_FOUND;
import static com.nowcoder.community.common.exception.CommonErrorCode.INVALID_ARGUMENT;

@Repository
public class MyBatisPostMediaAssetRepository implements PostMediaAssetRepository {

    private final PostMediaAssetMapper mapper;
    private final UuidV7Generator idGenerator;

    @Autowired
    public MyBatisPostMediaAssetRepository(PostMediaAssetMapper mapper) {
        this(mapper, new UuidV7Generator());
    }

    MyBatisPostMediaAssetRepository(PostMediaAssetMapper mapper, UuidV7Generator idGenerator) {
        this.mapper = mapper;
        this.idGenerator = idGenerator;
    }

    @Override
    public PostMediaAsset getRequired(UUID assetId) {
        PostMediaAssetDataObject row = mapper.selectById(assetId);
        if (row == null) {
            throw new BusinessException(NOT_FOUND, "媒体资源不存在");
        }
        return toDomain(row);
    }

    @Override
    public List<PostMediaAsset> listByIds(List<UUID> assetIds) {
        if (assetIds == null || assetIds.isEmpty()) {
            return List.of();
        }
        List<UUID> cleanIds = assetIds.stream()
                .filter(id -> id != null)
                .distinct()
                .toList();
        if (cleanIds.isEmpty()) {
            return List.of();
        }
        return mapper.selectByIds(cleanIds).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public List<PostMediaAsset> listByPostId(UUID postId) {
        if (postId == null) {
            return List.of();
        }
        return mapper.selectByPostId(postId).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public UUID createDraft(PostMediaAsset asset) {
        UUID assetId = asset.id() == null ? idGenerator.next() : asset.id();
        mapper.insert(toRow(assetId, asset));
        return assetId;
    }

    @Override
    public void markUploaded(UUID assetId, UUID versionId, String publicUrl, Date updateTime) {
        mapper.markUploaded(assetId, versionId, publicUrl, updateTime);
    }

    @Override
    public boolean claimUploadCompletion(UUID assetId, UUID actorUserId, long operationVersion, Date updateTime) {
        return mapper.claimUploadCompletion(assetId, actorUserId, operationVersion, updateTime) == 1;
    }

    @Override
    public boolean markObjectCompleted(
            UUID assetId,
            long operationVersion,
            UUID versionId,
            String publicUrl,
            String contentType,
            long contentLength,
            Date updateTime
    ) {
        return mapper.markObjectCompleted(
                assetId, operationVersion, versionId, publicUrl, contentType, contentLength, updateTime) == 1;
    }

    @Override
    public boolean markUploadCompleted(UUID assetId, long operationVersion, Date updateTime) {
        return mapper.markUploadCompleted(assetId, operationVersion, updateTime) == 1;
    }

    @Override
    public boolean markUploadFailed(UUID assetId, long operationVersion, String failureReason, Date updateTime) {
        return mapper.markUploadFailed(
                assetId, operationVersion, normalizeFailureReason(failureReason), updateTime) == 1;
    }

    @Override
    public boolean resetStaleUploadCompletion(
            UUID assetId,
            long operationVersion,
            Date staleBefore,
            Date resetAt
    ) {
        if (assetId == null || operationVersion <= 0L || staleBefore == null || resetAt == null) {
            return false;
        }
        return mapper.resetStaleUploadCompletion(
                assetId, operationVersion, staleBefore, resetAt) == 1;
    }

    @Override
    public boolean recordUploadRecoveryFailure(
            UUID assetId,
            long operationVersion,
            Date staleBefore,
            String failureReason,
            Date updateTime
    ) {
        if (assetId == null
                || operationVersion <= 0L
                || staleBefore == null
                || updateTime == null
                || !updateTime.after(staleBefore)) {
            return false;
        }
        return mapper.recordUploadRecoveryFailure(
                assetId,
                operationVersion,
                staleBefore,
                normalizeFailureReason(failureReason),
                updateTime
        ) == 1;
    }

    @Override
    public List<PostMediaAsset> listStaleCompleting(Date updatedBefore, int limit) {
        if (updatedBefore == null) {
            return List.of();
        }
        int safeLimit = Math.min(500, Math.max(1, limit));
        return mapper.listStaleCompleting(updatedBefore, safeLimit).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public void markDraftDeleted(UUID assetId, Date updateTime) {
        mapper.markDraftDeleted(assetId, updateTime);
    }

    @Override
    public void bindToPost(UUID assetId, UUID postId, UUID ossReferenceId, PostVideoState videoState, Date updateTime) {
        mapper.bindToPost(assetId, postId, ossReferenceId, videoState.name(), updateTime);
    }

    @Override
    public void releaseRemovedFromPost(UUID postId, List<UUID> keepIds, Date updateTime) {
        List<UUID> cleanKeepIds = keepIds == null
                ? List.of()
                : keepIds.stream().filter(id -> id != null).distinct().toList();
        mapper.releaseRemovedFromPost(postId, cleanKeepIds, updateTime);
    }

    @Override
    public long requestBind(
            UUID assetId,
            UUID postId,
            UUID ossReferenceId,
            PostVideoState videoState,
            Date updateTime
    ) {
        if (assetId == null || postId == null || ossReferenceId == null || videoState == null || updateTime == null) {
            throw new BusinessException(INVALID_ARGUMENT, "媒体引用绑定请求非法");
        }
        if (mapper.requestBind(assetId, postId, ossReferenceId, videoState.name(), updateTime) != 1) {
            throw new BusinessException(INVALID_ARGUMENT, "媒体引用状态不允许绑定");
        }
        return requiredRow(assetId).getReferenceOperationVersion();
    }

    @Override
    public boolean markBound(UUID assetId, long operationVersion, Date updateTime) {
        if (assetId == null || operationVersion <= 0L || updateTime == null) {
            return false;
        }
        return mapper.markBound(assetId, operationVersion, updateTime) == 1;
    }

    @Override
    public long requestRelease(UUID assetId, Date updateTime) {
        if (assetId == null || updateTime == null) {
            throw new BusinessException(INVALID_ARGUMENT, "媒体引用释放请求非法");
        }
        if (mapper.requestRelease(assetId, updateTime) != 1) {
            throw new BusinessException(INVALID_ARGUMENT, "媒体引用状态不允许释放");
        }
        return requiredRow(assetId).getReferenceOperationVersion();
    }

    @Override
    public long requestBindRepair(UUID assetId, Date updateTime) {
        if (assetId == null || updateTime == null) {
            throw new BusinessException(INVALID_ARGUMENT, "媒体引用绑定修复请求非法");
        }
        if (mapper.requestBindRepair(assetId, updateTime) != 1) {
            throw new BusinessException(INVALID_ARGUMENT, "媒体引用状态不允许绑定修复");
        }
        return requiredRow(assetId).getReferenceOperationVersion();
    }

    @Override
    public long requestReleaseRepair(UUID assetId, Date updateTime) {
        if (assetId == null || updateTime == null) {
            throw new BusinessException(INVALID_ARGUMENT, "媒体引用释放修复请求非法");
        }
        if (mapper.requestReleaseRepair(assetId, updateTime) != 1) {
            throw new BusinessException(INVALID_ARGUMENT, "媒体引用状态不允许释放修复");
        }
        return requiredRow(assetId).getReferenceOperationVersion();
    }

    @Override
    public boolean markReleased(UUID assetId, long operationVersion, Date updateTime) {
        if (assetId == null || operationVersion <= 0L || updateTime == null) {
            return false;
        }
        return mapper.markReleased(assetId, operationVersion, updateTime) == 1;
    }

    @Override
    public List<PostMediaAsset> listPending(int limit) {
        int safeLimit = Math.min(500, Math.max(1, limit));
        return mapper.listPending(safeLimit).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public List<PostMediaAsset> scanReferenceStatesAfter(UUID afterAssetId, int limit) {
        UUID cursor = afterAssetId == null ? new UUID(0L, 0L) : afterAssetId;
        int safeLimit = Math.min(500, Math.max(1, limit));
        return mapper.scanReferenceStatesAfter(cursor, safeLimit).stream()
                .map(this::toDomain)
                .toList();
    }

    private PostMediaAssetDataObject toRow(UUID assetId, PostMediaAsset asset) {
        PostMediaAssetDataObject row = new PostMediaAssetDataObject();
        row.setId(assetId);
        row.setOwnerUserId(asset.ownerUserId());
        row.setPostId(asset.postId());
        row.setOssObjectId(asset.ossObjectId());
        row.setOssVersionId(asset.ossVersionId());
        row.setOssReferenceId(asset.ossReferenceId());
        row.setUploadSessionId(asset.uploadSessionId());
        row.setFileName(asset.fileName());
        row.setContentType(asset.contentType());
        row.setContentLength(asset.contentLength());
        row.setMediaKind(asset.mediaKind().name());
        row.setLifecycle(asset.lifecycle().name());
        row.setUploadStatus(asset.uploadStatus().name());
        row.setUploadOperationVersion(asset.uploadOperationVersion());
        row.setUploadUpdatedAt(asset.uploadUpdatedAt());
        row.setReferenceStatus(asset.referenceStatus().name());
        row.setReferenceOperationVersion(asset.referenceOperationVersion());
        row.setReferenceUpdatedAt(asset.referenceUpdatedAt());
        row.setVideoState(asset.videoState().name());
        row.setPublicUrl(asset.publicUrl());
        row.setFailureReason(asset.failureReason());
        row.setCreateTime(asset.createTime());
        row.setUpdateTime(asset.updateTime());
        return row;
    }

    private PostMediaAsset toDomain(PostMediaAssetDataObject row) {
        return new PostMediaAsset(
                row.getId(),
                row.getOwnerUserId(),
                row.getPostId(),
                row.getOssObjectId(),
                row.getOssVersionId(),
                row.getOssReferenceId(),
                row.getUploadSessionId(),
                row.getFileName(),
                row.getContentType(),
                row.getContentLength(),
                PostMediaKind.valueOf(row.getMediaKind()),
                PostMediaAssetLifecycle.valueOf(row.getLifecycle()),
                PostMediaUploadStatus.valueOf(row.getUploadStatus()),
                row.getUploadOperationVersion(),
                row.getUploadUpdatedAt(),
                PostMediaReferenceStatus.valueOf(row.getReferenceStatus()),
                row.getReferenceOperationVersion(),
                row.getReferenceUpdatedAt(),
                PostVideoState.valueOf(row.getVideoState()),
                row.getPublicUrl(),
                row.getFailureReason(),
                row.getCreateTime(),
                row.getUpdateTime()
        );
    }

    private PostMediaAssetDataObject requiredRow(UUID assetId) {
        PostMediaAssetDataObject row = mapper.selectById(assetId);
        if (row == null) {
            throw new BusinessException(NOT_FOUND, "媒体资源不存在");
        }
        return row;
    }

    private static String normalizeFailureReason(String failureReason) {
        String safeReason = failureReason == null ? "" : failureReason.trim();
        return safeReason.length() <= 512 ? safeReason : safeReason.substring(0, 512);
    }
}
