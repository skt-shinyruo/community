package com.nowcoder.community.content.service;

import com.nowcoder.community.infra.tx.AfterCommitExecutor;
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.content.entity.DiscussPost;
import com.nowcoder.community.content.domain.event.PostDomainEventPublisher;
import com.nowcoder.community.content.score.PostScoreQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
import java.util.Locale;

import static com.nowcoder.community.content.exception.ContentErrorCode.POST_NOT_FOUND;
import static com.nowcoder.community.common.exception.CommonErrorCode.FORBIDDEN;
import static com.nowcoder.community.common.exception.CommonErrorCode.INVALID_ARGUMENT;

/**
 * 帖子写路径命令服务：
 * - 聚合“写库 + 发布事件 + 非 DB 副作用”（热度刷新队列）到同一事务域
 * - 通过 After-Commit 将副作用延后到事务提交后执行，避免回滚导致的幽灵行为
 */
@Service
public class PostCommandService {

    private static final Logger log = LoggerFactory.getLogger(PostCommandService.class);
    private static final String CATEGORY_BUSINESS = "business";
    private static final String CATEGORY_ASYNC = "async";
    private static final String MDC_CATEGORY = "community.category";
    private static final String MDC_ACTION = "community.action";
    private static final String MDC_OUTCOME = "community.outcome";

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
                warnEvent(
                        CATEGORY_ASYNC,
                        "post_score_enqueue",
                        "degraded",
                        e,
                        "community.reason_code", "enqueue_failed",
                        "community.target_type", "post",
                        "community.target_id", postId
                );
            }
        });
        infoEvent(
                CATEGORY_BUSINESS,
                "post_create",
                "success",
                "user.id", userId,
                "community.post_category_id", categoryId,
                "community.target_type", "post",
                "community.target_id", postId
        );

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
                warnEvent(
                        CATEGORY_ASYNC,
                        "post_score_enqueue",
                        "degraded",
                        e,
                        "community.reason_code", "enqueue_failed",
                        "community.target_type", "post",
                        "community.target_id", postId
                );
            }
        });
        infoEvent(
                CATEGORY_BUSINESS,
                "post_update",
                "success",
                "user.id", actorUserId,
                "community.post_category_id", categoryId,
                "community.target_type", "post",
                "community.target_id", postId
        );
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
        infoEvent(
                CATEGORY_BUSINESS,
                "post_delete",
                "success",
                "community.reason_code", "author_delete",
                "user.id", actorUserId,
                "community.target_type", "post",
                "community.target_id", postId
        );
    }

    @Transactional
    public void topPost(int actorUserId, int postId) {
        if (actorUserId <= 0 || postId <= 0) {
            throw new BusinessException(INVALID_ARGUMENT, "actorUserId/postId 非法");
        }
        postService.getById(postId);
        postService.updateType(postId, 1);
        domainEventPublisher.postUpdated(postId);
        infoEvent(
                CATEGORY_BUSINESS,
                "post_top",
                "success",
                "user.id", actorUserId,
                "community.target_type", "post",
                "community.target_id", postId
        );
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
                warnEvent(
                        CATEGORY_ASYNC,
                        "post_score_enqueue",
                        "degraded",
                        e,
                        "community.reason_code", "enqueue_failed",
                        "community.target_type", "post",
                        "community.target_id", postId
                );
            }
        });
        infoEvent(
                CATEGORY_BUSINESS,
                "post_wonderful",
                "success",
                "user.id", actorUserId,
                "community.target_type", "post",
                "community.target_id", postId
        );
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
        infoEvent(
                CATEGORY_BUSINESS,
                "post_delete",
                "success",
                "community.reason_code", "admin_delete",
                "user.id", actorUserId,
                "community.target_type", "post",
                "community.target_id", postId
        );
    }

    private void infoEvent(String category, String action, String outcome, Object... keyValues) {
        logEvent(category, action, outcome, false, null, keyValues);
    }

    private void warnEvent(String category, String action, String outcome, Throwable throwable, Object... keyValues) {
        logEvent(category, action, outcome, true, throwable, keyValues);
    }

    private void logEvent(String category, String action, String outcome, boolean warn, Throwable throwable, Object... keyValues) {
        if (keyValues.length % 2 != 0) {
            throw new IllegalArgumentException("Post event keyValues must contain key/value pairs");
        }
        String previousCategory = MDC.get(MDC_CATEGORY);
        String previousAction = MDC.get(MDC_ACTION);
        String previousOutcome = MDC.get(MDC_OUTCOME);
        MDC.put(MDC_CATEGORY, category);
        MDC.put(MDC_ACTION, action);
        MDC.put(MDC_OUTCOME, outcome);
        try {
            String message = buildMessage(category, action, outcome, keyValues);
            if (warn) {
                if (throwable == null) {
                    log.warn(message);
                } else {
                    log.warn(message, throwable);
                }
                return;
            }
            log.info(message);
        } finally {
            restore(MDC_CATEGORY, previousCategory);
            restore(MDC_ACTION, previousAction);
            restore(MDC_OUTCOME, previousOutcome);
        }
    }

    private String buildMessage(String category, String action, String outcome, Object... keyValues) {
        StringBuilder message = new StringBuilder(160);
        appendToken(message, MDC_CATEGORY, category);
        appendToken(message, MDC_ACTION, action);
        appendToken(message, MDC_OUTCOME, outcome);
        for (int i = 0; i < keyValues.length; i += 2) {
            appendToken(message, String.valueOf(keyValues[i]), keyValues[i + 1]);
        }
        return message.toString();
    }

    private void appendToken(StringBuilder message, String key, Object value) {
        if (message.length() > 0) {
            message.append(' ');
        }
        message.append(key).append('=').append(encodeTokenValue(value));
    }

    private String encodeTokenValue(Object value) {
        if (value == null) {
            return "-";
        }
        String raw = String.valueOf(value);
        if (raw.isEmpty()) {
            return "-";
        }
        StringBuilder encoded = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char ch = raw.charAt(i);
            if (Character.isWhitespace(ch) || Character.isISOControl(ch) || ch == '=' || ch == '%') {
                encoded.append('%');
                String hex = Integer.toHexString(ch).toUpperCase(Locale.ROOT);
                if (hex.length() == 1) {
                    encoded.append('0');
                }
                encoded.append(hex);
            } else {
                encoded.append(ch);
            }
        }
        return encoded.toString();
    }

    private void restore(String key, String previousValue) {
        if (previousValue == null) {
            MDC.remove(key);
            return;
        }
        MDC.put(key, previousValue);
    }
}
