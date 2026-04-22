// 收藏 MyBatis Mapper：提供收藏关系写入、删除、查询与收藏列表分页。
package com.nowcoder.community.content.mapper;

import com.nowcoder.community.content.entity.DiscussPost;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Date;
import java.util.List;
import java.util.UUID;

@Mapper
public interface BookmarkMapper {

    int insertBookmark(@Param("userId") UUID userId, @Param("postId") UUID postId, @Param("createTime") Date createTime);

    int deleteBookmark(@Param("userId") UUID userId, @Param("postId") UUID postId);

    int existsBookmark(@Param("userId") UUID userId, @Param("postId") UUID postId);

    List<DiscussPost> selectBookmarkedPosts(@Param("userId") UUID userId, @Param("offset") int offset, @Param("limit") int limit);
}
