package com.nowcoder.community.user.application;

import com.nowcoder.community.common.json.JsonCodecException;
import com.nowcoder.community.user.application.command.DispatchUserEventCommand;
import com.nowcoder.community.user.contracts.event.UserContractEvent;
import com.nowcoder.community.user.contracts.event.UserContractEventCodec;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Objects;

@Service
public class UserEventDispatchApplicationService {

    private final UserContractEventCodec contractEventCodec;
    private final UserIntegrationEventDispatcher dispatcher;

    public UserEventDispatchApplicationService(
            UserContractEventCodec contractEventCodec,
            UserIntegrationEventDispatcher dispatcher
    ) {
        this.contractEventCodec = contractEventCodec;
        this.dispatcher = dispatcher;
    }

    public void dispatch(DispatchUserEventCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        if (!StringUtils.hasText(command.payloadJson())) {
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
            UserContractEvent envelope = contractEventCodec.deserialize(payloadJson);
            try {
                contractEventCodec.decode(envelope);
            } catch (IllegalArgumentException error) {
                throw new IllegalStateException(error.getMessage(), error);
            }
            return envelope;
        } catch (JsonCodecException e) {
            throw new IllegalStateException("user event outbox payload deserialization failed", e);
        }
    }
}
