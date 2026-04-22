package com.nowcoder.community.content.mapper;

import com.nowcoder.community.content.entity.Comment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.UUID;

@Mapper
public interface CommentMapper {

    List<Comment> selectCommentsByEntity(int entityType, UUID entityId, int offset, int limit);

    List<Comment> selectRecentCommentsByUser(@Param("userId") UUID userId, @Param("offset") int offset, @Param("limit") int limit);

    int selectCountByEntity(int entityType, UUID entityId);

    /**
     * 查询指定帖子集合的“最后活动”（包含：直接评论 + 回复评论）。
     * <p>
     * 返回的 Comment 实体中：
     * - entityId = postId
     * - userId = 最后回复人 userId
     * - createTime = 最后回复时间
     */
    List<Comment> selectLatestPostActivitiesByPostIds(@Param("postIds") List<UUID> postIds);

    int insertComment(Comment comment);

    Comment selectCommentById(UUID id);

    int existsPostComment(@Param("postId") UUID postId, @Param("commentId") UUID commentId);

    int updateCommentContent(
            @Param("id") UUID id,
            @Param("content") String content,
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
