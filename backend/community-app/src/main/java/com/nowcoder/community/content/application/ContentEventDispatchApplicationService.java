package com.nowcoder.community.content.application;

import com.nowcoder.community.common.json.JsonCodecException;
import com.nowcoder.community.content.application.command.DispatchContentEventCommand;
import com.nowcoder.community.content.contracts.event.ContentContractEvent;
import com.nowcoder.community.content.contracts.event.ContentContractEventCodec;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Objects;

@Service
public class ContentEventDispatchApplicationService {

    private final ContentContractEventCodec contractEventCodec;
    private final ContentIntegrationEventDispatcher dispatcher;

    public ContentEventDispatchApplicationService(
            ContentContractEventCodec contractEventCodec,
            ContentIntegrationEventDispatcher dispatcher
    ) {
        this.contractEventCodec = contractEventCodec;
        this.dispatcher = dispatcher;
    }

    public void dispatch(DispatchContentEventCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        if (!StringUtils.hasText(command.payloadJson())) {
            throw new IllegalStateException("content event outbox payload is blank");
        }

        ContentContractEvent contractEvent = parseContractEvent(command.payloadJson());
        if (!StringUtils.hasText(contractEvent.eventId())) {
            throw new IllegalStateException("content event outbox payload missing eventId");
        }
        if (!StringUtils.hasText(contractEvent.type())) {
            throw new IllegalStateException("content event outbox payload missing type");
        }
        if (contractEvent.aggregateId() == null) {
            throw new IllegalStateException("content event outbox payload missing aggregateId");
        }
        if (!StringUtils.hasText(contractEvent.aggregateType())) {
            throw new IllegalStateException("content event outbox payload missing aggregateType");
        }
        if (contractEvent.occurredAt() == null) {
            throw new IllegalStateException("content event outbox payload missing occurredAt");
        }
        if (contractEvent.version() <= 0L) {
            throw new IllegalStateException("content event outbox payload missing version");
        }
        dispatcher.dispatch(command.eventKey(), contractEvent);
    }

    private ContentContractEvent parseContractEvent(String payloadJson) {
        try {
            ContentContractEvent envelope = contractEventCodec.deserialize(payloadJson);
            try {
                contractEventCodec.decode(envelope);
            } catch (IllegalArgumentException error) {
                throw new IllegalStateException(error.getMessage(), error);
            }
            return envelope;
        } catch (JsonCodecException e) {
            throw new IllegalStateException("content event outbox payload deserialization failed", e);
        }
    }
}
