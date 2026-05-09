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
}
