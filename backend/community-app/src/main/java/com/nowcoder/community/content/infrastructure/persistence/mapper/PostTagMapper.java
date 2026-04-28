package com.nowcoder.community.content.infrastructure.persistence.mapper;

import com.nowcoder.community.content.domain.model.PostTagName;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Date;
import java.util.List;
import java.util.UUID;

@Mapper
public interface PostTagMapper {

    int insertPostTag(@Param("postId") UUID postId, @Param("tagId") UUID tagId, @Param("createTime") Date createTime);

    List<PostTagName> selectTagNamesByPostIds(@Param("postIds") List<UUID> postIds);

    int deleteTagsByPostId(@Param("postId") UUID postId);
}
