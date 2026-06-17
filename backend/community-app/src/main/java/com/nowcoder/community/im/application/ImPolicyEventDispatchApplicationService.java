package com.nowcoder.community.im.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.nowcoder.community.common.json.JsonCodec;
import com.nowcoder.community.common.json.JsonCodecException;
import com.nowcoder.community.im.common.event.UserBlockRelationChanged;
import com.nowcoder.community.im.common.event.UserMessagingPolicyChanged;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.UUID;

@Service
@ConditionalOnProperty(prefix = "events.outbox", name = "enabled", havingValue = "true")
public class ImPolicyEventDispatchApplicationService {

    private final JsonCodec jsonCodec;
    private final ImPolicyEventKafkaDispatchPort dispatchPort;
    private final String userMessagingPolicyChangedTopic;
    private final String userBlockRelationChangedTopic;

    public ImPolicyEventDispatchApplicationService(
            JsonCodec jsonCodec,
            ImPolicyEventKafkaDispatchPort dispatchPort,
            @Value("${im.kafka.topics.event-user-messaging-policy-changed:im.event.user-messaging-policy-changed}") String userMessagingPolicyChangedTopic,
            @Value("${im.kafka.topics.event-user-block-relation-changed:im.event.user-block-relation-changed}") String userBlockRelationChangedTopic
    ) {
        this.jsonCodec = jsonCodec;
        this.dispatchPort = dispatchPort;
        this.userMessagingPolicyChangedTopic = userMessagingPolicyChangedTopic;
        this.userBlockRelationChangedTopic = userBlockRelationChangedTopic;
    }

    public void dispatch(String outboxEventId, String outboxKey, String payloadJson) {
        if (!StringUtils.hasText(payloadJson)) {
            return;
        }

        JsonNode payload;
        try {
            payload = jsonCodec.readTree(payloadJson);
        } catch (JsonCodecException e) {
            throw new IllegalStateException("im policy outbox payload 反序列化失败", e);
        }

        String kind = text(payload, "kind");
        if ("USER_POLICY".equalsIgnoreCase(kind) || "MODERATION".equalsIgnoreCase(kind)) {
            publishModerationState(outboxEventId, payload);
            return;
        }
        if ("BLOCK".equalsIgnoreCase(kind)) {
            publishBlockState(outboxEventId, payload);
        }
    }

    private void publishModerationState(String eventId, JsonNode payload) {
        UUID userId = firstUuid(payload, "primaryUserId", "userId");
        if (userId == null) {
            return;
        }
        UserMessagingPolicyChanged changed = new UserMessagingPolicyChanged(
                eventId,
                userId,
                booleanValue(payload, "userExists"),
                booleanValue(payload, "suspended"),
                booleanValue(payload, "muted"),
                longValue(payload, "muteUntil"),
                longValue(payload, "banUntil"),
                booleanValue(payload, "canSendPrivate"),
                requiredLongValue(payload, "occurredAtEpochMillis"),
                longValue(payload, "version")
        );
        dispatchPort.send(userMessagingPolicyChangedTopic, userId.toString(), changed);
    }

    private void publishBlockState(String eventId, JsonNode payload) {
        UUID blockerUserId = firstUuid(payload, "primaryUserId", "blockerUserId");
        UUID blockedUserId = firstUuid(payload, "secondaryUserId", "blockedUserId");
        if (blockerUserId == null || blockedUserId == null) {
            return;
        }
        UserBlockRelationChanged changed = new UserBlockRelationChanged(
                eventId,
                blockerUserId,
                blockedUserId,
                booleanValue(payload, "active"),
                requiredLongValue(payload, "occurredAtEpochMillis"),
                longValue(payload, "version")
        );
        dispatchPort.send(userBlockRelationChangedTopic, blockerUserId.toString(), changed);
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
            throw new IllegalStateException("im policy outbox payload missing required field: " + fieldName);
        }
        return value;
    }
}
