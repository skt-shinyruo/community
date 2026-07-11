package com.nowcoder.community.im.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.nowcoder.community.common.json.JsonCodec;
import com.nowcoder.community.common.json.JsonCodecException;
import com.nowcoder.community.im.application.command.DispatchImPolicyEventCommand;
import com.nowcoder.community.im.common.event.UserBlockRelationChanged;
import com.nowcoder.community.im.common.event.UserMessagingPolicyChanged;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Objects;
import java.util.UUID;

@Service
public class ImPolicyEventDispatchApplicationService {

    private final JsonCodec jsonCodec;
    private final ImPolicyIntegrationEventDispatcher dispatcher;

    public ImPolicyEventDispatchApplicationService(
            JsonCodec jsonCodec,
            ImPolicyIntegrationEventDispatcher dispatcher
    ) {
        this.jsonCodec = jsonCodec;
        this.dispatcher = dispatcher;
    }

    public void dispatch(DispatchImPolicyEventCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        if (!StringUtils.hasText(command.outboxEventId())) {
            throw new IllegalArgumentException("im policy outbox event id is blank");
        }
        if (!StringUtils.hasText(command.payloadJson())) {
            throw new IllegalArgumentException("im policy outbox payload is blank");
        }

        JsonNode payload;
        try {
            payload = jsonCodec.readTree(command.payloadJson());
        } catch (JsonCodecException e) {
            throw new IllegalStateException("im policy outbox payload 反序列化失败", e);
        }

        String kind = text(payload, "kind");
        if ("USER_POLICY".equals(kind)) {
            publishModerationState(command.outboxEventId(), payload);
            return;
        }
        if ("BLOCK".equals(kind)) {
            publishBlockState(command.outboxEventId(), payload);
            return;
        }
        throw new IllegalArgumentException("im policy outbox payload kind is unsupported: " + kind);
    }

    private void publishModerationState(String eventId, JsonNode payload) {
        UUID userId = uuid(payload, "primaryUserId");
        if (userId == null) {
            throw malformed("USER_POLICY", "primaryUserId");
        }
        long occurredAtEpochMillis = requiredLongValue(payload, "occurredAtEpochMillis");
        long version = requiredLongValue(payload, "version");
        UserMessagingPolicyChanged changed = new UserMessagingPolicyChanged(
                eventId,
                userId,
                requiredBooleanValue(payload, "userExists"),
                requiredBooleanValue(payload, "suspended"),
                requiredBooleanValue(payload, "muted"),
                longValue(payload, "muteUntil"),
                longValue(payload, "banUntil"),
                requiredBooleanValue(payload, "canSendPrivate"),
                occurredAtEpochMillis,
                version
        );
        dispatcher.dispatchUserMessagingPolicyChanged(userId.toString(), changed);
    }

    private void publishBlockState(String eventId, JsonNode payload) {
        UUID blockerUserId = uuid(payload, "primaryUserId");
        UUID blockedUserId = uuid(payload, "secondaryUserId");
        if (blockerUserId == null || blockedUserId == null) {
            throw malformed("BLOCK", "primaryUserId/secondaryUserId");
        }
        long occurredAtEpochMillis = requiredLongValue(payload, "occurredAtEpochMillis");
        long version = requiredLongValue(payload, "version");
        UserBlockRelationChanged changed = new UserBlockRelationChanged(
                eventId,
                blockerUserId,
                blockedUserId,
                requiredBooleanValue(payload, "active"),
                occurredAtEpochMillis,
                version
        );
        dispatcher.dispatchUserBlockRelationChanged(blockerUserId.toString(), changed);
    }

    private String text(JsonNode node, String fieldName) {
        if (node == null || fieldName == null) {
            return null;
        }
        JsonNode value = node.get(fieldName);
        return value == null || value.isNull() ? null : value.asText(null);
    }

    private UUID uuid(JsonNode node, String fieldName) {
        String value = text(node, fieldName);
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private boolean requiredBooleanValue(JsonNode node, String fieldName) {
        JsonNode value = node.get(fieldName);
        if (value == null || !value.isBoolean()) {
            throw new IllegalArgumentException("im policy outbox payload missing required field: " + fieldName);
        }
        return value.booleanValue();
    }

    private Long longValue(JsonNode node, String fieldName) {
        if (node == null || fieldName == null) {
            return null;
        }
        JsonNode value = node.get(fieldName);
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isIntegralNumber()) {
            throw new IllegalArgumentException("im policy outbox payload field must be an integer: " + fieldName);
        }
        return value.longValue();
    }

    private long requiredLongValue(JsonNode node, String fieldName) {
        Long value = longValue(node, fieldName);
        if (value == null || value <= 0L) {
            throw new IllegalArgumentException("im policy outbox payload missing required field: " + fieldName);
        }
        return value;
    }

    private IllegalArgumentException malformed(String kind, String field) {
        return new IllegalArgumentException(
                "im policy outbox payload malformed " + kind + ": " + field);
    }
}
