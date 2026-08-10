package com.nowcoder.community.content.application;

import com.nowcoder.community.common.json.event.OwnerEventDispatchSupport;
import com.nowcoder.community.content.contracts.event.ContentContractEvent;
import com.nowcoder.community.content.contracts.event.ContentContractEventCodec;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
public class ContentEventDispatchApplicationService {

    private final OwnerEventDispatchSupport<ContentContractEvent> dispatchSupport;

    public ContentEventDispatchApplicationService(
            ContentContractEventCodec contractEventCodec,
            ContentIntegrationEventDispatcher dispatcher
    ) {
        this.dispatchSupport = OwnerEventDispatchSupport.versioned(
                "content",
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

    public void dispatch(DispatchContentEventCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        dispatchSupport.dispatch(command.eventKey(), command.payloadJson());
    }

    public record DispatchContentEventCommand(String eventKey, String payloadJson) {
    }
}
