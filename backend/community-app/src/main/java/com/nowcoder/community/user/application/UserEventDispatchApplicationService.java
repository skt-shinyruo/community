package com.nowcoder.community.user.application;

import com.nowcoder.community.common.json.event.OwnerEventDispatchSupport;
import com.nowcoder.community.user.contracts.event.UserContractEvent;
import com.nowcoder.community.user.contracts.event.UserContractEventCodec;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
public class UserEventDispatchApplicationService {

    private final OwnerEventDispatchSupport<UserContractEvent> dispatchSupport;

    public UserEventDispatchApplicationService(
            UserContractEventCodec contractEventCodec,
            UserIntegrationEventDispatcher dispatcher
    ) {
        this.dispatchSupport = OwnerEventDispatchSupport.basic(
                "user",
                contractEventCodec::deserialize,
                contractEventCodec::decode,
                event -> new OwnerEventDispatchSupport.EnvelopeMetadata(event.eventId(), event.type()),
                dispatcher::dispatch
        );
    }

    public void dispatch(DispatchUserEventCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        dispatchSupport.dispatch(command.eventKey(), command.payloadJson());
    }

    public record DispatchUserEventCommand(String eventKey, String payloadJson) {
    }
}
