package com.nowcoder.community.content.service;

// 评论领域服务：负责评论写入与基础校验、评论事件发布。
import com.nowcoder.community.social.application.BlockQueryApplicationService;
import com.nowcoder.community.content.api.event.payload.CommentPayload;
import com.nowcoder.community.contracts.domain.EntityTypes;
import com.nowcoder.community.contracts.exception.BusinessException;
import com.nowcoder.community.infra.pagination.Pagination;
import com.nowcoder.community.infra.tx.AfterCommitExecutor;
import com.nowcoder.community.content.dao.CommentMapper;
import com.nowcoder.community.content.entity.Comment;
import com.nowcoder.community.content.entity.DiscussPost;
import com.nowcoder.community.content.event.ContentEventPublisher;
import com.nowcoder.community.content.score.PostScoreQueue;
import com.nowcoder.community.content.text.ContentTextCodec;
import com.nowcoder.community.content.util.SensitiveFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.nowcoder.community.content.api.ContentErrorCode.COMMENT_NOT_FOUND;
import static com.nowcoder.community.contracts.api.CommonErrorCode.FORBIDDEN;
import static com.nowcoder.community.contracts.api.CommonErrorCode.INVALID_ARGUMENT;
import static com.nowcoder.community.contracts.api.CommonErrorCode.NOT_FOUND;

@Service
public class CommentService {

    public static final int ENTITY_TYPE_POST = EntityTypes.POST;
    public static final int ENTITY_TYPE_COMMENT = EntityTypes.COMMENT;

    private static final Logger log = LoggerFactory.getLogger(CommentService.class);

    private final CommentMapper commentMapper;
    private final PostService postService;
    private final SensitiveFilter sensitiveFilter;
    private final PostScoreQueue postScoreQueue;
    private final ContentEventPublisher eventPublisher;
    private final BlockQueryApplicationService blockQueryApplicationService;
    private final UserModerationGuard moderationGuard;
    private final ContentTextCodec textCodec;

    public CommentService(
            CommentMapper commentMapper,
            PostService postService,
            SensitiveFilter sensitiveFilter,
            PostScoreQueue postScoreQueue,
            ContentEventPublisher eventPublisher,
            BlockQueryApplicationService blockQueryApplicationService,
            UserModerationGuard moderationGuard,
            ContentTextCodec textCodec
    ) {
        this.commentMapper = commentMapper;
        this.postService = postService;
        this.sensitiveFilter = sensitiveFilter;
        this.postScoreQueue = postScoreQueue;
        this.eventPublisher = eventPublisher;
        this.blockQueryApplicationService = blockQueryApplicationService;
        this.moderationGuard = moderationGuard;
        this.textCodec = textCodec;
    }

    public List<Comment> listByPost(int postId, int page, int size) {
        int p = Math.max(0, page);
        int s = Math.min(50, Math.max(1, size));
        return commentMapper.selectCommentsByEntity(ENTITY_TYPE_POST, postId, Pagination.safeOffset(p, s), s);
    }

    public List<Comment> listReplies(int commentId, int page, int size) {
        int p = Math.max(0, page);
        int s = Math.min(50, Math.max(1, size));
        return commentMapper.selectCommentsByEntity(ENTITY_TYPE_COMMENT, commentId, Pagination.safeOffset(p, s), s);
    }

    public void assertCommentBelongsToPost(int postId, int commentId) {
        if (postId <= 0 || commentId <= 0) {
            throw new BusinessException(INVALID_ARGUMENT, "postId/commentId 非法");
        }
        // 先校验帖子存在（避免通过 commentId 侧信道探测帖子状态）
        postService.getById(postId);
        int count = commentMapper.existsPostComment(postId, commentId);
        if (count <= 0) {
            throw new BusinessException(NOT_FOUND, "资源不存在");
        }
    }

    /**
     * 批量查询帖子“最后活动”（包含直接评论 + 回复评论）。
     * 返回 Map 的 key 为 postId（通过 Comment.entityId 复用承载）。
     */
    public Map<Integer, Comment> getLatestPostActivitiesByPostIds(List<Integer> postIds) {
        Map<Integer, Comment> map = new HashMap<>();
        if (postIds == null || postIds.isEmpty()) {
            return map;
        }

        List<Comment> rows = commentMapper.selectLatestPostActivitiesByPostIds(postIds);
        if (rows == null || rows.isEmpty()) {
            return map;
        }

        // SQL 已按 post_id asc, cid desc 排序：每个 post_id 的第一条即为 tie-break 后的最终结果。
        for (Comment c : rows) {
            if (c == null) continue;
            int postId = c.getEntityId();
            if (postId <= 0) continue;
            map.putIfAbsent(postId, c);
        }
        return map;
    }

    @Transactional
    public int addComment(int actorUserId, int postId, Integer entityType, Integer entityId, Integer targetId, String content) {
        if (actorUserId <= 0 || postId <= 0) {
            throw new BusinessException(INVALID_ARGUMENT, "actorUserId/postId 非法");
        }

        assertCanSpeak(actorUserId);

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
            if (targetComment == null || targetComment.getId() <= 0 || targetComment.getStatus() != 0) {
                throw new BusinessException(NOT_FOUND, "资源不存在");
            }

            // 统一回复语义：仅允许回复“该帖子下的一级评论”，避免跨帖/多层回复写入后读侧不可达。
            if (targetComment.getEntityType() != ENTITY_TYPE_POST || targetComment.getEntityId() != postId) {
                throw new BusinessException(NOT_FOUND, "资源不存在");
            }
            targetUserId = targetComment.getUserId();
            if (targetId == null || targetId <= 0) {
                targetId = targetUserId;
            }
        } else {
            throw new BusinessException(INVALID_ARGUMENT, "entityType 非法");
        }

        // 反骚扰：双方任意一方拉黑另一方，都禁止互动（评论/回复）。
        if (targetUserId != null && targetUserId > 0) {
            if (blockQueryApplicationService != null && blockQueryApplicationService.isEitherBlocked(actorUserId, targetUserId)) {
                throw new BusinessException(FORBIDDEN, "双方存在拉黑关系，无法执行该操作");
            }
        }

        String safe = textCodec.escapeOnWrite(content == null ? "" : content.trim());
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

        // commentCount 语义：包含一级评论 + 回复（不回填历史，只对新写入生效）。
        postService.incrementCommentCount(postId, 1);

        // 热度刷新属于非 DB 副作用：延后到事务提交后，避免回滚仍触发刷新。
        AfterCommitExecutor.runAfterCommit(() -> {
            try {
                postScoreQueue.add(postId);
            } catch (RuntimeException e) {
                log.warn("[post-score] enqueue failed after commit (postId={}): {}", postId, e.toString());
            }
        });

        CommentPayload payload = new CommentPayload();
        payload.setCommentId(comment.getId());
        payload.setPostId(postId);
        payload.setUserId(actorUserId);
        payload.setEntityType(type);
        payload.setEntityId(eid);
        payload.setTargetUserId(targetUserId);
        payload.setContent(textCodec.decodeOnRead(safe));
        payload.setCreateTime(Instant.now());
        eventPublisher.publishCommentCreated(payload);

        return comment.getId();
    }

    @Transactional
    public void updateComment(int actorUserId, int postId, int commentId, String content) {
        if (actorUserId <= 0 || postId <= 0 || commentId <= 0) {
            throw new BusinessException(INVALID_ARGUMENT, "actorUserId/postId/commentId 非法");
        }

        assertCanSpeak(actorUserId);

        // postId 先做存在性校验（避免跨帖编辑）
        postService.getById(postId);

        Comment existed = commentMapper.selectCommentById(commentId);
        if (existed == null || existed.getId() <= 0) {
            throw new BusinessException(COMMENT_NOT_FOUND);
        }
        if (existed.getStatus() != 0) {
            throw new BusinessException(COMMENT_NOT_FOUND);
        }
        if (existed.getUserId() != actorUserId) {
            throw new BusinessException(FORBIDDEN, "只能编辑自己的评论");
        }

        // 15 分钟窗口
        Date now = new Date();
        if (existed.getCreateTime() == null) {
            throw new BusinessException(INVALID_ARGUMENT, "评论时间非法");
        }
        long windowMillis = 15L * 60 * 1000;
        if (now.getTime() - existed.getCreateTime().getTime() > windowMillis) {
            throw new BusinessException(FORBIDDEN, "已超过可编辑时间（15min）");
        }

        // 跨帖校验：直接评论 / 回复评论
        if (existed.getEntityType() == ENTITY_TYPE_POST) {
            if (existed.getEntityId() != postId) {
                throw new BusinessException(INVALID_ARGUMENT, "commentId 不属于该帖子");
            }
        } else if (existed.getEntityType() == ENTITY_TYPE_COMMENT) {
            Comment parent = commentMapper.selectCommentById(existed.getEntityId());
            if (parent == null || parent.getId() <= 0 || parent.getEntityType() != ENTITY_TYPE_POST || parent.getEntityId() != postId) {
                throw new BusinessException(INVALID_ARGUMENT, "commentId 不属于该帖子");
            }
        }

        String safe = textCodec.escapeOnWrite(content == null ? "" : content.trim());
        safe = sensitiveFilter.filter(safe);

        int updated = commentMapper.updateCommentContent(commentId, safe, now);
        if (updated <= 0) {
            throw new BusinessException(INVALID_ARGUMENT, "更新评论失败");
        }
    }

    private void assertCanSpeak(int userId) {
        moderationGuard.assertCanSpeak(userId);
    }
}
