package com.nowcoder.community.im.projection;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.common.outbox.OutboxEvent;
import com.nowcoder.community.common.outbox.OutboxHandler;
import com.nowcoder.community.im.common.ImTopics;
import com.nowcoder.community.im.common.event.UserBlockRelationChanged;
import com.nowcoder.community.im.common.event.UserMessagingPolicyChanged;
import com.nowcoder.community.social.api.query.SocialBlockQueryApi;
import com.nowcoder.community.user.api.model.UserModerationStateView;
import com.nowcoder.community.user.api.model.UserSummaryView;
import com.nowcoder.community.user.api.query.UserLookupQueryApi;
import com.nowcoder.community.user.api.query.UserModerationQueryApi;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletionException;

@Component
@ConditionalOnClass(KafkaTemplate.class)
@ConditionalOnProperty(prefix = "events.outbox", name = "enabled", havingValue = "true")
public class ImPolicyKafkaOutboxHandler implements OutboxHandler {

    public static final String TOPIC = ImPolicyChangePublisher.TOPIC;

    private final ObjectMapper objectMapper;
    private final UserModerationQueryApi userModerationQueryApi;
    private final SocialBlockQueryApi socialBlockQueryApi;
    private final UserLookupQueryApi userLookupQueryApi;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public ImPolicyKafkaOutboxHandler(
            ObjectMapper objectMapper,
            UserModerationQueryApi userModerationQueryApi,
            SocialBlockQueryApi socialBlockQueryApi,
            UserLookupQueryApi userLookupQueryApi,
            KafkaTemplate<String, Object> kafkaTemplate
    ) {
        this.objectMapper = objectMapper;
        this.userModerationQueryApi = userModerationQueryApi;
        this.socialBlockQueryApi = socialBlockQueryApi;
        this.userLookupQueryApi = userLookupQueryApi;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Override
    public String topic() {
        return TOPIC;
    }

    @Override
    public void handle(OutboxEvent event) {
        if (event == null || !StringUtils.hasText(event.payload())) {
            return;
        }

        JsonNode payload;
        try {
            payload = objectMapper.readTree(event.payload());
        } catch (Exception e) {
            throw new IllegalStateException("im policy outbox payload 反序列化失败", e);
        }

        String kind = text(payload, "kind");
        if ("MODERATION".equalsIgnoreCase(kind)) {
            publishModerationState(event, firstUuid(payload, "primaryUserId", "userId"));
            return;
        }
        if ("BLOCK".equalsIgnoreCase(kind)) {
            publishBlockState(
                    event,
                    firstUuid(payload, "primaryUserId", "blockerUserId"),
                    firstUuid(payload, "secondaryUserId", "blockedUserId")
            );
        }
    }

    private void publishModerationState(OutboxEvent event, UUID userId) {
        if (userId == null) {
            return;
        }
        Instant now = Instant.now();
        UserSummaryView summary = userLookupQueryApi.getSummaryById(userId);
        boolean userExists = summary != null && summary.id() != null;
        boolean suspended = false;
        boolean muted = false;
        Long muteUntil = null;
        Long banUntil = null;
        if (userExists) {
            UserModerationStateView state = userModerationQueryApi.getModerationState(userId);
            muteUntil = toEpochMillis(state == null ? null : state.muteUntil());
            banUntil = toEpochMillis(state == null ? null : state.banUntil());
            suspended = isActive(state == null ? null : state.banUntil(), now);
            muted = isActive(state == null ? null : state.muteUntil(), now);
        }

        UserMessagingPolicyChanged changed = new UserMessagingPolicyChanged(
                event.eventId(),
                userId,
                userExists,
                suspended,
                muted,
                muteUntil,
                banUntil,
                userExists && !suspended && !muted,
                now.toEpochMilli()
        );
        sendToKafka(ImTopics.EVENT_USER_MESSAGING_POLICY_CHANGED, userId.toString(), changed);
    }

    private void publishBlockState(OutboxEvent event, UUID blockerUserId, UUID blockedUserId) {
        if (blockerUserId == null || blockedUserId == null) {
            return;
        }
        UserBlockRelationChanged changed = new UserBlockRelationChanged(
                event.eventId(),
                blockerUserId,
                blockedUserId,
                socialBlockQueryApi.hasBlocked(blockerUserId, blockedUserId),
                Instant.now().toEpochMilli()
        );
        sendToKafka(ImTopics.EVENT_USER_BLOCK_RELATION_CHANGED, blockerUserId.toString(), changed);
    }

    private void sendToKafka(String topic, String key, Object value) {
        try {
            kafkaTemplate.send(topic, key, value).join();
        } catch (CompletionException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            throw new IllegalStateException("im policy kafka publish failed: " + topic, cause);
        }
    }

    private boolean isActive(Instant until, Instant now) {
        return until != null && until.isAfter(now);
    }

    private Long toEpochMillis(Instant until) {
        return until == null ? null : until.toEpochMilli();
    }

    private String text(JsonNode node, String fieldName) {
        if (node == null || fieldName == null) {
            return null;
        }
        JsonNode value = node.get(fieldName);
        return value == null || value.isNull() ? null : value.asText(null);
    }

    private UUID firstUuid(JsonNode node, String... fieldNames) {
        for (String fieldName : fieldNames) {
            String value = text(node, fieldName);
            if (!StringUtils.hasText(value)) {
                continue;
            }
            try {
                return UUID.fromString(value.trim());
            } catch (IllegalArgumentException ignored) {
            }
        }
        return null;
    }
}
