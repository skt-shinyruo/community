package com.nowcoder.community.content.infrastructure.persistence;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.id.UuidV7Generator;
import com.nowcoder.community.content.domain.model.PostMediaAsset;
import com.nowcoder.community.content.domain.model.PostMediaAssetLifecycle;
import com.nowcoder.community.content.domain.model.PostMediaKind;
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
                PostVideoState.valueOf(row.getVideoState()),
                row.getPublicUrl(),
                row.getFailureReason(),
                row.getCreateTime(),
                row.getUpdateTime()
        );
    }
}
