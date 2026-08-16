package com.nowcoder.community.content.application;

import com.nowcoder.community.common.logging.EventLogFields;
import com.nowcoder.community.common.logging.EventLogMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class PostBusinessEventLogger {

    private static final Logger log = LoggerFactory.getLogger(PostBusinessEventLogger.class);
    private static final String CATEGORY_BUSINESS = "business";
    private static final String MDC_CATEGORY = EventLogFields.EVENT_CATEGORY;
    private static final String MDC_ACTION = EventLogFields.EVENT_ACTION;
    private static final String MDC_OUTCOME = EventLogFields.EVENT_OUTCOME;

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
        String previousCategory = MDC.get(MDC_CATEGORY);
        String previousAction = MDC.get(MDC_ACTION);
        String previousOutcome = MDC.get(MDC_OUTCOME);
        MDC.put(MDC_CATEGORY, category);
        MDC.put(MDC_ACTION, action);
        MDC.put(MDC_OUTCOME, outcome);
        try {
            log.info(EventLogMessage.format(keyValues));
        } finally {
            restore(MDC_CATEGORY, previousCategory);
            restore(MDC_ACTION, previousAction);
            restore(MDC_OUTCOME, previousOutcome);
        }
    }

    private void restore(String key, String previousValue) {
        if (previousValue == null) {
            MDC.remove(key);
            return;
        }
        MDC.put(key, previousValue);
    }
}
