package com.nowcoder.community.content.service;

import com.nowcoder.community.content.app.post.AdminDeletePostUseCase;
import com.nowcoder.community.content.app.post.CreatePostUseCase;
import com.nowcoder.community.content.app.post.DeleteOwnPostUseCase;
import com.nowcoder.community.content.app.post.MarkPostWonderfulUseCase;
import com.nowcoder.community.content.app.post.TopPostUseCase;
import com.nowcoder.community.content.app.post.UpdatePostUseCase;
import com.nowcoder.community.common.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

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

    private final CreatePostUseCase createPostUseCase;
    private final UpdatePostUseCase updatePostUseCase;
    private final DeleteOwnPostUseCase deleteOwnPostUseCase;
    private final TopPostUseCase topPostUseCase;
    private final MarkPostWonderfulUseCase markPostWonderfulUseCase;
    private final AdminDeletePostUseCase adminDeletePostUseCase;

    public PostCommandService(
            CreatePostUseCase createPostUseCase,
            UpdatePostUseCase updatePostUseCase,
            DeleteOwnPostUseCase deleteOwnPostUseCase,
            TopPostUseCase topPostUseCase,
            MarkPostWonderfulUseCase markPostWonderfulUseCase,
            AdminDeletePostUseCase adminDeletePostUseCase
    ) {
        this.createPostUseCase = createPostUseCase;
        this.updatePostUseCase = updatePostUseCase;
        this.deleteOwnPostUseCase = deleteOwnPostUseCase;
        this.topPostUseCase = topPostUseCase;
        this.markPostWonderfulUseCase = markPostWonderfulUseCase;
        this.adminDeletePostUseCase = adminDeletePostUseCase;
    }

    public UUID createPost(UUID userId, String title, String content, UUID categoryId, List<String> tags) {
        UUID postId = createPostUseCase.createPost(userId, title, content, categoryId, tags);
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

    public void updatePost(UUID actorUserId, UUID postId, String title, String content, UUID categoryId, List<String> tags) {
        updatePostUseCase.updatePost(actorUserId, postId, title, content, categoryId, tags);
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

    public void deletePostByAuthor(UUID actorUserId, UUID postId) {
        deleteOwnPostUseCase.deletePostByAuthor(actorUserId, postId);
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

    public void topPost(UUID actorUserId, UUID postId) {
        topPostUseCase.topPost(actorUserId, postId);
        infoEvent(
                CATEGORY_BUSINESS,
                "post_top",
                "success",
                "user.id", actorUserId,
                "community.target_type", "post",
                "community.target_id", postId
        );
    }

    public void markWonderful(UUID actorUserId, UUID postId) {
        markPostWonderfulUseCase.markWonderful(actorUserId, postId);
        infoEvent(
                CATEGORY_BUSINESS,
                "post_wonderful",
                "success",
                "user.id", actorUserId,
                "community.target_type", "post",
                "community.target_id", postId
        );
    }

    public void adminDelete(UUID actorUserId, UUID postId) {
        adminDeletePostUseCase.adminDelete(actorUserId, postId);
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
