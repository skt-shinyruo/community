// 收藏 MyBatis Mapper：提供收藏关系写入、删除、查询与收藏列表分页。
package com.nowcoder.community.content.infrastructure.persistence.mapper;

import com.nowcoder.community.content.domain.model.DiscussPost;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Date;
import java.util.List;
import java.util.UUID;

@Mapper
public interface BookmarkMapper {

    UUID lockActivePost(@Param("postId") UUID postId);

    UUID lockPost(@Param("postId") UUID postId);

    int insertBookmarkForActivePost(
            @Param("userId") UUID userId,
            @Param("postId") UUID postId,
            @Param("createTime") Date createTime
    );

    int existsActivePost(@Param("postId") UUID postId);

    int deleteBookmark(@Param("userId") UUID userId, @Param("postId") UUID postId);

    int existsBookmark(@Param("userId") UUID userId, @Param("postId") UUID postId);

    long countByPostId(@Param("postId") UUID postId);

    List<DiscussPost> selectBookmarkedPosts(@Param("userId") UUID userId, @Param("offset") int offset, @Param("limit") int limit);
}
