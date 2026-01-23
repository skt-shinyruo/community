// 收藏 MyBatis Mapper：提供收藏关系写入、删除、查询与收藏列表分页。
package com.nowcoder.community.content.dao;

import com.nowcoder.community.content.entity.DiscussPost;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Date;
import java.util.List;

@Mapper
public interface BookmarkMapper {

    int insertBookmark(@Param("userId") int userId, @Param("postId") int postId, @Param("createTime") Date createTime);

    int deleteBookmark(@Param("userId") int userId, @Param("postId") int postId);

    int existsBookmark(@Param("userId") int userId, @Param("postId") int postId);

    List<DiscussPost> selectBookmarkedPosts(@Param("userId") int userId, @Param("offset") int offset, @Param("limit") int limit);
}

