package com.nowcoder.community.content.infrastructure.persistence.mapper;

import com.nowcoder.community.content.infrastructure.persistence.dataobject.PostContentBlockDataObject;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.UUID;

@Mapper
public interface PostContentBlockMapper {

    int insert(PostContentBlockDataObject row);

    int deleteByPostId(@Param("postId") UUID postId);

    List<PostContentBlockDataObject> selectByPostId(@Param("postId") UUID postId);

    List<PostContentBlockDataObject> selectByPostIds(@Param("postIds") List<UUID> postIds);
}
