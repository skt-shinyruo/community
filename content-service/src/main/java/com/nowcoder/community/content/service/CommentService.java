package com.nowcoder.community.content.service;

import com.nowcoder.community.common.event.payload.CommentPayload;
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.content.dao.CommentMapper;
import com.nowcoder.community.content.entity.Comment;
import com.nowcoder.community.content.entity.DiscussPost;
import com.nowcoder.community.content.event.ContentEventPublisher;
import com.nowcoder.community.content.score.PostScoreQueue;
import com.nowcoder.community.content.util.SensitiveFilter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.HtmlUtils;

import java.time.Instant;
import java.util.Date;
import java.util.List;

import static com.nowcoder.community.common.api.CommonErrorCode.INVALID_ARGUMENT;
import static com.nowcoder.community.common.api.CommonErrorCode.NOT_FOUND;

@Service
public class CommentService {

    public static final int ENTITY_TYPE_POST = 1;
    public static final int ENTITY_TYPE_COMMENT = 2;

    private final CommentMapper commentMapper;
    private final PostService postService;
    private final SensitiveFilter sensitiveFilter;
    private final PostScoreQueue postScoreQueue;
    private final ContentEventPublisher eventPublisher;

    public CommentService(
            CommentMapper commentMapper,
            PostService postService,
            SensitiveFilter sensitiveFilter,
            PostScoreQueue postScoreQueue,
            ContentEventPublisher eventPublisher
    ) {
        this.commentMapper = commentMapper;
        this.postService = postService;
        this.sensitiveFilter = sensitiveFilter;
        this.postScoreQueue = postScoreQueue;
        this.eventPublisher = eventPublisher;
    }

    public List<Comment> listByPost(int postId, int page, int size) {
        int p = Math.max(0, page);
        int s = Math.min(50, Math.max(1, size));
        return commentMapper.selectCommentsByEntity(ENTITY_TYPE_POST, postId, p * s, s);
    }

    public List<Comment> listReplies(int commentId, int page, int size) {
        int p = Math.max(0, page);
        int s = Math.min(50, Math.max(1, size));
        return commentMapper.selectCommentsByEntity(ENTITY_TYPE_COMMENT, commentId, p * s, s);
    }

    @Transactional
    public int addComment(int actorUserId, int postId, Integer entityType, Integer entityId, Integer targetId, String content) {
        if (actorUserId <= 0 || postId <= 0) {
            throw new BusinessException(INVALID_ARGUMENT, "actorUserId/postId 非法");
        }

        int type = entityType == null ? ENTITY_TYPE_POST : entityType;
        int eid;
        Integer targetUserId;

        DiscussPost post = postService.getById(postId);

        if (type == ENTITY_TYPE_POST) {
            eid = postId;
            targetUserId = post.getUserId();
            targetId = 0;
        } else if (type == ENTITY_TYPE_COMMENT) {
            if (entityId == null || entityId <= 0) {
                throw new BusinessException(INVALID_ARGUMENT, "回复评论时 entityId(commentId) 不能为空");
            }
            eid = entityId;
            Comment targetComment = commentMapper.selectCommentById(eid);
            if (targetComment == null) {
                throw new BusinessException(NOT_FOUND, "被回复的评论不存在");
            }
            targetUserId = targetComment.getUserId();
            if (targetId == null || targetId <= 0) {
                targetId = targetUserId;
            }
        } else {
            throw new BusinessException(INVALID_ARGUMENT, "entityType 非法");
        }

        String safe = HtmlUtils.htmlEscape(content == null ? "" : content.trim());
        safe = sensitiveFilter.filter(safe);

        Comment comment = new Comment();
        comment.setUserId(actorUserId);
        comment.setEntityType(type);
        comment.setEntityId(eid);
        comment.setTargetId(targetId == null ? 0 : Math.max(0, targetId));
        comment.setContent(safe);
        comment.setStatus(0);
        comment.setCreateTime(new Date());

        commentMapper.insertComment(comment);

        if (type == ENTITY_TYPE_POST) {
            int commentCount = commentMapper.selectCountByEntity(ENTITY_TYPE_POST, postId);
            postService.updateCommentCount(postId, commentCount);
            postScoreQueue.add(postId);
        }

        CommentPayload payload = new CommentPayload();
        payload.setCommentId(comment.getId());
        payload.setPostId(postId);
        payload.setUserId(actorUserId);
        payload.setEntityType(type);
        payload.setEntityId(eid);
        payload.setTargetUserId(targetUserId);
        payload.setContent(safe);
        payload.setCreateTime(Instant.now());
        eventPublisher.publishCommentCreated(payload);

        return comment.getId();
    }
}
