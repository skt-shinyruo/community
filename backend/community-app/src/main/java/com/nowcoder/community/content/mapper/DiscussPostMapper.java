package com.nowcoder.community.content.mapper;

// 帖子数据访问层：负责帖子查询与状态/计数等更新操作。
import com.nowcoder.community.content.entity.DiscussPost;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.UUID;

@Mapper
public interface DiscussPostMapper {

    List<DiscussPost> selectDiscussPosts(
            @Param("userId") UUID userId,
            @Param("categoryId") UUID categoryId,
            @Param("categoryIds") java.util.List<UUID> categoryIds,
            @Param("tag") String tag,
            @Param("offset") int offset,
            @Param("limit") int limit,
            @Param("orderMode") int orderMode
    );

    List<DiscussPost> selectDiscussPostsByIds(@Param("postIds") List<UUID> postIds);

    /**
     * 供内部重建索引等后台任务使用：按主键游标向后扫描帖子，避免 offset 分页在大表上的性能问题。
     */
    List<DiscussPost> selectDiscussPostsAfterId(@Param("afterId") UUID afterId, @Param("limit") int limit);

    int selectDiscussPostRows(@Param("userId") int userId);

    int insertDiscussPost(DiscussPost discussPost);

    DiscussPost selectDiscussPostById(UUID id);

    int updateCommentCount(UUID id, int commentCount);

    /**
     * 原子增量更新 comment_count，避免并发覆盖。
     */
    int incrementCommentCount(@Param("id") UUID id, @Param("delta") int delta);

    int updateType(@Param("id") UUID id, @Param("type") int type);

    int updateStatus(@Param("id") UUID id, @Param("status") int status);

    int updateScore(UUID id, double score);

    int updatePostContent(
            @Param("id") UUID id,
            @Param("title") String title,
            @Param("content") String content,
            @Param("categoryId") UUID categoryId,
            @Param("updateTime") java.util.Date updateTime
    );

    int updateModerationDeleteMeta(
            @Param("id") UUID id,
            @Param("status") int status,
            @Param("deletedBy") UUID deletedBy,
            @Param("deletedReason") String deletedReason,
            @Param("deletedTime") java.util.Date deletedTime
    );
}
