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

    void bindToPost(UUID assetId, UUID postId, UUID ossReferenceId, PostVideoState videoState, Date updateTime);

    void releaseRemovedFromPost(UUID postId, List<UUID> keepIds, Date updateTime);
}
