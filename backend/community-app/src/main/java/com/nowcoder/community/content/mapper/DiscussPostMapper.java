package com.nowcoder.community.content.mapper;

// 帖子数据访问层：负责帖子查询与状态/计数等更新操作。
import com.nowcoder.community.content.entity.DiscussPost;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface DiscussPostMapper {

    List<DiscussPost> selectDiscussPosts(
            @Param("userId") int userId,
            @Param("categoryId") Integer categoryId,
            @Param("categoryIds") java.util.List<Integer> categoryIds,
            @Param("tag") String tag,
            @Param("offset") int offset,
            @Param("limit") int limit,
            @Param("orderMode") int orderMode
    );

    /**
     * 供内部重建索引等后台任务使用：按主键游标向后扫描帖子，避免 offset 分页在大表上的性能问题。
     */
    List<DiscussPost> selectDiscussPostsAfterId(@Param("afterId") int afterId, @Param("limit") int limit);

    int selectDiscussPostRows(@Param("userId") int userId);

    int insertDiscussPost(DiscussPost discussPost);

    DiscussPost selectDiscussPostById(int id);

    int updateCommentCount(int id, int commentCount);

    /**
     * 原子增量更新 comment_count，避免并发覆盖。
     */
    int incrementCommentCount(@Param("id") int id, @Param("delta") int delta);

    int updateType(@Param("id") int id, @Param("type") int type);

    int updateStatus(@Param("id") int id, @Param("status") int status);

    int updateScore(int id, double score);

    int updatePostContent(
            @Param("id") int id,
            @Param("title") String title,
            @Param("content") String content,
            @Param("categoryId") Integer categoryId,
            @Param("updateTime") java.util.Date updateTime
    );

    int updateModerationDeleteMeta(
            @Param("id") int id,
            @Param("status") int status,
            @Param("deletedBy") int deletedBy,
            @Param("deletedReason") String deletedReason,
            @Param("deletedTime") java.util.Date deletedTime
    );
}
