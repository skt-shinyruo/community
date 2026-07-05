package com.nowcoder.community.user.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.nowcoder.community.common.json.JsonCodec;
import com.nowcoder.community.common.json.JsonCodecException;
import com.nowcoder.community.user.application.command.DispatchUserEventCommand;
import com.nowcoder.community.user.contracts.event.UserContractEvent;
import com.nowcoder.community.user.contracts.event.UserEventTypes;
import com.nowcoder.community.user.contracts.event.UserPolicyChangedPayload;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@ConditionalOnExpression("'${user.events.publisher:outbox-kafka}' == 'outbox-kafka' && '${events.outbox.enabled:true}' == 'true'")
public class UserEventDispatchApplicationService {

    private final JsonCodec jsonCodec;
    private final UserIntegrationEventDispatcher dispatcher;

    public UserEventDispatchApplicationService(
            JsonCodec jsonCodec,
            UserIntegrationEventDispatcher dispatcher
    ) {
        this.jsonCodec = jsonCodec;
        this.dispatcher = dispatcher;
    }

    public void dispatch(DispatchUserEventCommand command) {
        if (command == null || !StringUtils.hasText(command.payloadJson())) {
            throw new IllegalStateException("user event outbox payload is blank");
        }

        UserContractEvent contractEvent = parseContractEvent(command.payloadJson());
        if (!StringUtils.hasText(contractEvent.eventId())) {
            throw new IllegalStateException("user event outbox payload missing eventId");
        }
        if (!StringUtils.hasText(contractEvent.type())) {
            throw new IllegalStateException("user event outbox payload missing type");
        }
        dispatcher.dispatch(command.eventKey(), contractEvent);
    }

    private UserContractEvent parseContractEvent(String payloadJson) {
        try {
            JsonNode root = jsonCodec.readTree(payloadJson);
            String eventId = text(root, "eventId");
            String type = text(root, "type");
            Object payload = typedPayload(type, root.get("payload"));
            return new UserContractEvent(eventId, type, payload);
        } catch (JsonCodecException e) {
            throw new IllegalStateException("user event outbox payload deserialization failed", e);
        }
    }

    private Object typedPayload(String type, JsonNode payload) {
        if (isKnownPayloadType(type) && (payload == null || payload.isNull())) {
            throw new IllegalStateException("user event outbox payload missing payload");
        }
        if (payload == null || payload.isNull()) {
            return null;
        }
        if (UserEventTypes.USER_POLICY_CHANGED.equals(type)) {
            return jsonCodec.treeToValue(payload, UserPolicyChangedPayload.class);
        }
        return jsonCodec.treeToValue(payload, Object.class);
    }

    private boolean isKnownPayloadType(String type) {
        return UserEventTypes.USER_POLICY_CHANGED.equals(type);
    }

    private String text(JsonNode node, String fieldName) {
        if (node == null || fieldName == null) {
            return null;
        }
        JsonNode value = node.get(fieldName);
        return value == null || value.isNull() ? null : value.asText(null);
    }
}
