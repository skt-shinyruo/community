package com.nowcoder.community.content.service;

import com.nowcoder.community.common.event.payload.PostPayload;
import com.nowcoder.community.common.tx.AfterCommitExecutor;
import com.nowcoder.community.content.entity.DiscussPost;
import com.nowcoder.community.content.event.ContentEventPublisher;
import com.nowcoder.community.content.projection.UserModerationProjectionRepository;
import com.nowcoder.community.content.score.PostScoreQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Date;
import java.util.List;

import static com.nowcoder.community.common.api.CommonErrorCode.FORBIDDEN;
import static com.nowcoder.community.common.api.CommonErrorCode.INVALID_ARGUMENT;
import static com.nowcoder.community.common.api.CommonErrorCode.NOT_FOUND;

/**
 * 帖子写路径命令服务：
 * - 聚合“写库 + 发布事件 + 非 DB 副作用”（热度刷新队列）到同一事务域
 * - 通过 After-Commit 将副作用延后到事务提交后执行，避免回滚导致的幽灵行为
 */
@Service
public class PostCommandService {

    private static final Logger log = LoggerFactory.getLogger(PostCommandService.class);

    private final PostService postService;
    private final PostScoreQueue postScoreQueue;
    private final ContentEventPublisher eventPublisher;
    private final CategoryService categoryService;
    private final TagService tagService;
    private final UserModerationProjectionRepository projectionRepository;
    private final UserModerationGuard moderationGuard;

    public PostCommandService(
            PostService postService,
            PostScoreQueue postScoreQueue,
            ContentEventPublisher eventPublisher,
            CategoryService categoryService,
            TagService tagService,
            UserModerationProjectionRepository projectionRepository,
            UserModerationGuard moderationGuard
    ) {
        this.postService = postService;
        this.postScoreQueue = postScoreQueue;
        this.eventPublisher = eventPublisher;
        this.categoryService = categoryService;
        this.tagService = tagService;
        this.projectionRepository = projectionRepository;
        this.moderationGuard = moderationGuard;
    }

    @Transactional
    public int createPost(int userId, String title, String content, Integer categoryId, List<String> tags) {
        if (userId <= 0) {
            throw new com.nowcoder.community.common.exception.BusinessException(INVALID_ARGUMENT, "userId 非法");
        }
        assertCanSpeak(userId);
        categoryService.assertExists(categoryId);

        DiscussPost post = new DiscussPost();
        post.setUserId(userId);
        post.setCategoryId(categoryId);
        post.setTitle(title);
        post.setContent(content);
        post.setType(0);
        post.setStatus(0);
        post.setCreateTime(new Date());
        post.setCommentCount(0);
        post.setScore(0.0);

        int postId = postService.create(post);

        // taxonomy：绑定 tags（同事务写入，便于后续列表/侧栏聚合使用）
        List<String> normalizedTags = tagService.bindTagsToPost(postId, tags);

        PostPayload payload = new PostPayload();
        payload.setPostId(postId);
        payload.setUserId(userId);
        payload.setCategoryId(categoryId);
        payload.setTags(normalizedTags);
        payload.setTitle(title);
        payload.setContent(content);
        payload.setType(post.getType());
        payload.setStatus(post.getStatus());
        payload.setCreateTime(post.getCreateTime() == null ? null : post.getCreateTime().toInstant());
        payload.setScore(post.getScore());

        eventPublisher.publishPostPublished(payload);

        // 热度刷新属于非 DB 副作用：延后到事务提交后，避免回滚仍触发刷新。
        AfterCommitExecutor.runAfterCommit(() -> {
            try {
                postScoreQueue.add(postId);
            } catch (Exception e) {
                log.warn("[post-score] enqueue failed after commit (postId={}): {}", postId, e.toString());
            }
        });

        return postId;
    }

    private void assertCanSpeak(int userId) {
        // 与 CommentService 统一：写路径不依赖 user-service 实时可用，改为读取本地投影（最终一致）
        moderationGuard.assertCanSpeak(userId);
    }

    @Transactional
    public void updatePost(int actorUserId, int postId, String title, String content, Integer categoryId, List<String> tags) {
        if (actorUserId <= 0 || postId <= 0) {
            throw new com.nowcoder.community.common.exception.BusinessException(INVALID_ARGUMENT, "actorUserId/postId 非法");
        }
        assertCanSpeak(actorUserId);
        categoryService.assertExists(categoryId);

        DiscussPost existed = postService.getByIdAllowDeleted(postId);
        if (existed.getStatus() == 2) {
            throw new com.nowcoder.community.common.exception.BusinessException(NOT_FOUND, "帖子不存在");
        }
        if (existed.getUserId() != actorUserId) {
            throw new com.nowcoder.community.common.exception.BusinessException(FORBIDDEN, "只能编辑自己的帖子");
        }

        Date now = new Date();
        if (existed.getCreateTime() == null) {
            throw new com.nowcoder.community.common.exception.BusinessException(INVALID_ARGUMENT, "帖子时间非法");
        }
        long windowMillis = 24L * 3600 * 1000;
        if (now.getTime() - existed.getCreateTime().getTime() > windowMillis) {
            throw new com.nowcoder.community.common.exception.BusinessException(FORBIDDEN, "已超过可编辑时间（24h）");
        }

        postService.updatePostContent(postId, title, content, categoryId, now);
        List<String> normalizedTags = tagService.replaceTagsForPost(postId, tags);

        PostPayload payload = new PostPayload();
        payload.setPostId(postId);
        payload.setUserId(existed.getUserId());
        payload.setCategoryId(categoryId);
        payload.setTags(normalizedTags);
        payload.setTitle(title);
        payload.setContent(content);
        payload.setType(existed.getType());
        payload.setStatus(existed.getStatus());
        payload.setCreateTime(existed.getCreateTime() == null ? null : existed.getCreateTime().toInstant());
        payload.setScore(existed.getScore());

        eventPublisher.publishPostUpdated(payload);
        AfterCommitExecutor.runAfterCommit(() -> {
            try {
                postScoreQueue.add(postId);
            } catch (Exception e) {
                log.warn("[post-score] enqueue failed after commit (postId={}): {}", postId, e.toString());
            }
        });
    }

    @Transactional
    public void deletePostByAuthor(int actorUserId, int postId) {
        if (actorUserId <= 0 || postId <= 0) {
            throw new com.nowcoder.community.common.exception.BusinessException(INVALID_ARGUMENT, "actorUserId/postId 非法");
        }
        DiscussPost existed = postService.getByIdAllowDeleted(postId);
        if (existed.getStatus() == 2) {
            throw new com.nowcoder.community.common.exception.BusinessException(NOT_FOUND, "帖子不存在");
        }
        if (existed.getUserId() != actorUserId) {
            throw new com.nowcoder.community.common.exception.BusinessException(FORBIDDEN, "只能删除自己的帖子");
        }
        postService.updateModerationDeleteMeta(postId, 2, actorUserId, "author_delete", new Date());

        List<String> tags = tagService.getTagsByPostIds(List.of(postId)).getOrDefault(postId, List.of());

        PostPayload payload = new PostPayload();
        payload.setPostId(postId);
        payload.setUserId(existed.getUserId());
        payload.setCategoryId(existed.getCategoryId());
        payload.setTags(tags);
        payload.setTitle(existed.getTitle());
        payload.setContent(existed.getContent());
        payload.setType(existed.getType());
        payload.setStatus(2);
        payload.setCreateTime(Instant.now());
        payload.setScore(existed.getScore());

        eventPublisher.publishPostDeleted(payload);
    }
}
