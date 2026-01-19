package com.nowcoder.community.content.dao;

import com.nowcoder.community.content.entity.DiscussPost;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface DiscussPostMapper {

    List<DiscussPost> selectDiscussPosts(int userId, int offset, int limit, int orderMode);

    /**
     * 供内部重建索引等后台任务使用：按主键游标向后扫描帖子，避免 offset 分页在大表上的性能问题。
     */
    List<DiscussPost> selectDiscussPostsAfterId(@Param("afterId") int afterId, @Param("limit") int limit);

    int selectDiscussPostRows(@Param("userId") int userId);

    int insertDiscussPost(DiscussPost discussPost);

    DiscussPost selectDiscussPostById(int id);

    int updateCommentCount(int id, int commentCount);

    int updateType(@Param("id") int id, @Param("type") int type);

    int updateStatus(@Param("id") int id, @Param("status") int status);

    int updateScore(int id, double score);
}
