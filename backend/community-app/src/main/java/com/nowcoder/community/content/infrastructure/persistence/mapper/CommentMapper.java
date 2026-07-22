package com.nowcoder.community.content.infrastructure.persistence.mapper;

import com.nowcoder.community.content.infrastructure.persistence.dataobject.CommentDataObject;
import com.nowcoder.community.content.infrastructure.persistence.dataobject.CommentTransitionTargetDataObject;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Date;
import java.util.List;
import java.util.UUID;

@Mapper
public interface CommentMapper {

    List<CommentDataObject> selectRootComments(
            @Param("postId") UUID postId,
            @Param("offset") int offset,
            @Param("limit") int limit
    );

    List<CommentDataObject> selectRootCommentsAfter(
            @Param("postId") UUID postId,
            @Param("boundaryTime") Date boundaryTime,
            @Param("boundaryId") UUID boundaryId,
            @Param("limit") int limit
    );

    List<CommentDataObject> selectRepliesByRootComment(
            @Param("rootCommentId") UUID rootCommentId,
            @Param("offset") int offset,
            @Param("limit") int limit
    );

    List<CommentDataObject> selectRepliesAfter(
            @Param("rootCommentId") UUID rootCommentId,
            @Param("boundaryTime") Date boundaryTime,
            @Param("boundaryId") UUID boundaryId,
            @Param("limit") int limit
    );

    List<CommentDataObject> selectRecentCommentsByUser(
            @Param("userId") UUID userId,
            @Param("offset") int offset,
            @Param("limit") int limit
    );

    List<CommentDataObject> selectLatestPostActivitiesByPostIds(@Param("postIds") List<UUID> postIds);

    int insert(CommentDataObject comment);

    CommentDataObject selectById(@Param("id") UUID id);

    CommentDataObject selectByIdForUpdate(@Param("id") UUID id);

    List<CommentDataObject> selectThreadForUpdate(@Param("rootCommentId") UUID rootCommentId);

    int existsRootComment(@Param("postId") UUID postId, @Param("commentId") UUID commentId);

    int applyEdit(
            @Param("commentId") UUID commentId,
            @Param("expectedVersion") long expectedVersion,
            @Param("content") String content,
            @Param("updateTime") Date updateTime
    );

    int applyDeletion(
            @Param("commentId") UUID commentId,
            @Param("expectedVersion") long expectedVersion,
            @Param("deletedBy") UUID deletedBy,
            @Param("deletedReason") String deletedReason,
            @Param("deletedTime") Date deletedTime
    );

    int applyThreadDeletion(
            @Param("rootCommentId") UUID rootCommentId,
            @Param("targets") List<CommentTransitionTargetDataObject> targets,
            @Param("deletedBy") UUID deletedBy,
            @Param("deletedReason") String deletedReason,
            @Param("deletedTime") Date deletedTime
    );
}
