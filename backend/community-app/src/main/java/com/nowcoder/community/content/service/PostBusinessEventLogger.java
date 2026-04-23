package com.nowcoder.community.content.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.UUID;

@Component
public class PostBusinessEventLogger {

    private static final Logger log = LoggerFactory.getLogger(PostBusinessEventLogger.class);
    private static final String CATEGORY_BUSINESS = "business";
    private static final String MDC_CATEGORY = "community.category";
    private static final String MDC_ACTION = "community.action";
    private static final String MDC_OUTCOME = "community.outcome";

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
            log.info(buildMessage(category, action, outcome, keyValues));
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
