package com.nowcoder.community.content.service;

import com.nowcoder.community.infra.tx.AfterCommitExecutor;
import com.nowcoder.community.contracts.exception.BusinessException;
import com.nowcoder.community.content.entity.DiscussPost;
import com.nowcoder.community.content.domain.event.PostDomainEventPublisher;
import com.nowcoder.community.content.score.PostScoreQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;

import static com.nowcoder.community.content.api.ContentErrorCode.POST_NOT_FOUND;
import static com.nowcoder.community.contracts.api.CommonErrorCode.FORBIDDEN;
import static com.nowcoder.community.contracts.api.CommonErrorCode.INVALID_ARGUMENT;

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
    private final CategoryService categoryService;
    private final TagService tagService;
    private final UserModerationGuard moderationGuard;
    private final PostDomainEventPublisher domainEventPublisher;

    public PostCommandService(
            PostService postService,
            PostScoreQueue postScoreQueue,
            CategoryService categoryService,
            TagService tagService,
            UserModerationGuard moderationGuard,
            PostDomainEventPublisher domainEventPublisher
    ) {
        this.postService = postService;
        this.postScoreQueue = postScoreQueue;
        this.categoryService = categoryService;
        this.tagService = tagService;
        this.moderationGuard = moderationGuard;
        this.domainEventPublisher = domainEventPublisher;
    }

    @Transactional
    public int createPost(int userId, String title, String content, Integer categoryId, List<String> tags) {
        if (userId <= 0) {
            throw new BusinessException(INVALID_ARGUMENT, "userId 非法");
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
        tagService.bindTagsToPost(postId, tags);
        domainEventPublisher.postPublished(postId);

        // 热度刷新属于非 DB 副作用：延后到事务提交后，避免回滚仍触发刷新。
        AfterCommitExecutor.runAfterCommit(() -> {
            try {
                postScoreQueue.add(postId);
            } catch (RuntimeException e) {
                log.warn("[post-score] enqueue failed after commit (postId={}): {}", postId, e.toString());
            }
        });

        return postId;
    }

    private void assertCanSpeak(int userId) {
        // 与 CommentService 统一：写路径通过 user 模块治理接口校验（fail-closed）。
        // 若未来需要解除同步依赖，可引入本地投影并保持最终一致。
        moderationGuard.assertCanSpeak(userId);
    }

    @Transactional
    public void updatePost(int actorUserId, int postId, String title, String content, Integer categoryId, List<String> tags) {
        if (actorUserId <= 0 || postId <= 0) {
            throw new BusinessException(INVALID_ARGUMENT, "actorUserId/postId 非法");
        }
        assertCanSpeak(actorUserId);
        categoryService.assertExists(categoryId);

        DiscussPost existed = postService.getByIdAllowDeleted(postId);
        if (existed.getStatus() == 2) {
            throw new BusinessException(POST_NOT_FOUND);
        }
        if (existed.getUserId() != actorUserId) {
            throw new BusinessException(FORBIDDEN, "只能编辑自己的帖子");
        }

        Date now = new Date();
        if (existed.getCreateTime() == null) {
            throw new BusinessException(INVALID_ARGUMENT, "帖子时间非法");
        }
        long windowMillis = 24L * 3600 * 1000;
        if (now.getTime() - existed.getCreateTime().getTime() > windowMillis) {
            throw new BusinessException(FORBIDDEN, "已超过可编辑时间（24h）");
        }

        postService.updatePostContent(postId, title, content, categoryId, now);
        tagService.replaceTagsForPost(postId, tags);
        domainEventPublisher.postUpdated(postId);
        AfterCommitExecutor.runAfterCommit(() -> {
            try {
                postScoreQueue.add(postId);
            } catch (RuntimeException e) {
                log.warn("[post-score] enqueue failed after commit (postId={}): {}", postId, e.toString());
            }
        });
    }

    @Transactional
    public void deletePostByAuthor(int actorUserId, int postId) {
        if (actorUserId <= 0 || postId <= 0) {
            throw new BusinessException(INVALID_ARGUMENT, "actorUserId/postId 非法");
        }
        DiscussPost existed = postService.getByIdAllowDeleted(postId);
        if (existed.getStatus() == 2) {
            throw new BusinessException(POST_NOT_FOUND);
        }
        if (existed.getUserId() != actorUserId) {
            throw new BusinessException(FORBIDDEN, "只能删除自己的帖子");
        }
        postService.updateModerationDeleteMeta(postId, 2, actorUserId, "author_delete", new Date());
        domainEventPublisher.postDeleted(postId);
    }

    @Transactional
    public void topPost(int actorUserId, int postId) {
        if (actorUserId <= 0 || postId <= 0) {
            throw new BusinessException(INVALID_ARGUMENT, "actorUserId/postId 非法");
        }
        postService.getById(postId);
        postService.updateType(postId, 1);
        domainEventPublisher.postUpdated(postId);
    }

    @Transactional
    public void markWonderful(int actorUserId, int postId) {
        if (actorUserId <= 0 || postId <= 0) {
            throw new BusinessException(INVALID_ARGUMENT, "actorUserId/postId 非法");
        }
        postService.getById(postId);
        postService.updateStatus(postId, 1);
        domainEventPublisher.postUpdated(postId);

        AfterCommitExecutor.runAfterCommit(() -> {
            try {
                postScoreQueue.add(postId);
            } catch (RuntimeException e) {
                log.warn("[post-score] enqueue failed after commit (postId={}): {}", postId, e.toString());
            }
        });
    }

    @Transactional
    public void adminDelete(int actorUserId, int postId) {
        if (actorUserId <= 0 || postId <= 0) {
            throw new BusinessException(INVALID_ARGUMENT, "actorUserId/postId 非法");
        }
        DiscussPost existed = postService.getByIdAllowDeleted(postId);
        if (existed.getStatus() == 2) {
            return;
        }
        postService.updateModerationDeleteMeta(postId, 2, actorUserId, "admin_delete", new Date());
        domainEventPublisher.postDeleted(postId);
    }
}
