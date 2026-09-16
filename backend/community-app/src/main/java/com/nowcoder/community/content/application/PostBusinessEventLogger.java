package com.nowcoder.community.content.application;

import com.nowcoder.community.common.logging.EventLogMdcScope;
import com.nowcoder.community.common.logging.EventLogMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class PostBusinessEventLogger {

    private static final Logger log = LoggerFactory.getLogger(PostBusinessEventLogger.class);
    private static final String CATEGORY_BUSINESS = "business";

    public void postCreate(UUID userId, UUID categoryId, UUID postId) {
        infoEvent(
                "post_create",
                "user.id", userId,
                "community.post_category_id", categoryId,
                "community.target_type", "post",
                "community.target_id", postId
        );
    }

    public void postUpdate(UUID userId, UUID categoryId, UUID postId) {
        infoEvent(
                "post_update",
                "user.id", userId,
                "community.post_category_id", categoryId,
                "community.target_type", "post",
                "community.target_id", postId
        );
    }

    public void postDeleteByAuthor(UUID userId, UUID postId) {
        infoEvent(
                "post_delete",
                "community.reason_code", "author_delete",
                "user.id", userId,
                "community.target_type", "post",
                "community.target_id", postId
        );
    }

    public void postTop(UUID userId, UUID postId) {
        infoEvent(
                "post_top",
                "user.id", userId,
                "community.target_type", "post",
                "community.target_id", postId
        );
    }

    public void postWonderful(UUID userId, UUID postId) {
        infoEvent(
                "post_wonderful",
                "user.id", userId,
                "community.target_type", "post",
                "community.target_id", postId
        );
    }

    public void postDeleteByAdmin(UUID userId, UUID postId) {
        infoEvent(
                "post_delete",
                "community.reason_code", "admin_delete",
                "user.id", userId,
                "community.target_type", "post",
                "community.target_id", postId
        );
    }

    private void infoEvent(String action, Object... keyValues) {
        logEvent(CATEGORY_BUSINESS, action, "success", keyValues);
    }

    private void logEvent(String category, String action, String outcome, Object... keyValues) {
        if (keyValues.length % 2 != 0) {
            throw new IllegalArgumentException("Post event keyValues must contain key/value pairs");
        }
        try (var ignored = EventLogMdcScope.open(category, action, outcome)) {
            log.info(EventLogMessage.format(keyValues));
        }
    }
}
