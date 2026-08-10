package com.nowcoder.community.social.application;

import com.nowcoder.community.common.json.event.OwnerEventDispatchSupport;
import com.nowcoder.community.social.contracts.event.SocialContractEvent;
import com.nowcoder.community.social.contracts.event.SocialContractEventCodec;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
public class SocialEventDispatchApplicationService {

    private final OwnerEventDispatchSupport<SocialContractEvent> dispatchSupport;

    public SocialEventDispatchApplicationService(
            SocialContractEventCodec contractEventCodec,
            SocialIntegrationEventDispatcher dispatcher
    ) {
        this.dispatchSupport = OwnerEventDispatchSupport.versioned(
                "social",
                contractEventCodec::deserialize,
                contractEventCodec::decode,
                event -> new OwnerEventDispatchSupport.VersionedEnvelopeMetadata(
                        event.eventId(),
                        event.type(),
                        event.aggregateId(),
                        event.aggregateType(),
                        event.occurredAt(),
                        event.version()
                ),
                dispatcher::dispatch
        );
    }

    public void dispatch(DispatchSocialEventCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        dispatchSupport.dispatch(command.eventKey(), command.payloadJson());
    }

    public record DispatchSocialEventCommand(String eventKey, String payloadJson) {
    }
}
