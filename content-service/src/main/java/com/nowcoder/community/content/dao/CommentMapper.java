package com.nowcoder.community.content.dao;

import com.nowcoder.community.content.entity.Comment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface CommentMapper {

    List<Comment> selectCommentsByEntity(int entityType, int entityId, int offset, int limit);

    int selectCountByEntity(int entityType, int entityId);

    /**
     * 查询指定帖子集合的“最后活动”（包含：直接评论 + 回复评论）。
     * <p>
     * 返回的 Comment 实体中：
     * - entityId = postId
     * - userId = 最后回复人 userId
     * - createTime = 最后回复时间
     */
    List<Comment> selectLatestPostActivitiesByPostIds(@Param("postIds") List<Integer> postIds);

    int insertComment(Comment comment);

    Comment selectCommentById(int id);

    int updateCommentContent(
            @Param("id") int id,
            @Param("content") String content,
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
