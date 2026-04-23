package com.nowcoder.community.im.projection;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.common.outbox.OutboxEvent;
import com.nowcoder.community.common.outbox.OutboxHandler;
import com.nowcoder.community.im.common.ImTopics;
import com.nowcoder.community.im.common.event.UserBlockRelationChanged;
import com.nowcoder.community.im.common.event.UserMessagingPolicyChanged;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.UUID;
import java.util.concurrent.CompletionException;

@Component
@ConditionalOnClass(KafkaTemplate.class)
@ConditionalOnProperty(prefix = "events.outbox", name = "enabled", havingValue = "true")
public class ImPolicyKafkaOutboxHandler implements OutboxHandler {

    public static final String TOPIC = ImPolicyChangePublisher.TOPIC;

    private final ObjectMapper objectMapper;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public ImPolicyKafkaOutboxHandler(
            ObjectMapper objectMapper,
            KafkaTemplate<String, Object> kafkaTemplate
    ) {
        this.objectMapper = objectMapper;
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
        if ("USER_POLICY".equalsIgnoreCase(kind) || "MODERATION".equalsIgnoreCase(kind)) {
            publishModerationState(event, payload);
            return;
        }
        if ("BLOCK".equalsIgnoreCase(kind)) {
            publishBlockState(event, payload);
        }
    }

    private void publishModerationState(OutboxEvent event, JsonNode payload) {
        UUID userId = firstUuid(payload, "primaryUserId", "userId");
        if (userId == null) {
            return;
        }
        UserMessagingPolicyChanged changed = new UserMessagingPolicyChanged(
                event.eventId(),
                userId,
                booleanValue(payload, "userExists"),
                booleanValue(payload, "suspended"),
                booleanValue(payload, "muted"),
                longValue(payload, "muteUntil"),
                longValue(payload, "banUntil"),
                booleanValue(payload, "canSendPrivate"),
                requiredLongValue(payload, "occurredAtEpochMillis")
        );
        sendToKafka(ImTopics.EVENT_USER_MESSAGING_POLICY_CHANGED, userId.toString(), changed);
    }

    private void publishBlockState(OutboxEvent event, JsonNode payload) {
        UUID blockerUserId = firstUuid(payload, "primaryUserId", "blockerUserId");
        UUID blockedUserId = firstUuid(payload, "secondaryUserId", "blockedUserId");
        if (blockerUserId == null || blockedUserId == null) {
            return;
        }
        UserBlockRelationChanged changed = new UserBlockRelationChanged(
                event.eventId(),
                blockerUserId,
                blockedUserId,
                booleanValue(payload, "active"),
                requiredLongValue(payload, "occurredAtEpochMillis")
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

    private boolean booleanValue(JsonNode node, String fieldName) {
        if (node == null || fieldName == null) {
            return false;
        }
        JsonNode value = node.get(fieldName);
        return value != null && !value.isNull() && value.asBoolean(false);
    }

    private Long longValue(JsonNode node, String fieldName) {
        if (node == null || fieldName == null) {
            return null;
        }
        JsonNode value = node.get(fieldName);
        return value == null || value.isNull() ? null : value.asLong();
    }

    private long requiredLongValue(JsonNode node, String fieldName) {
        Long value = longValue(node, fieldName);
        if (value == null) {
            throw new IllegalStateException("im policy outbox payload 缺少字段: " + fieldName);
        }
        return value;
    }
}
