package com.nowcoder.community.social.application;

import com.nowcoder.community.common.json.JsonCodecException;
import com.nowcoder.community.social.application.command.DispatchSocialEventCommand;
import com.nowcoder.community.social.contracts.event.SocialContractEvent;
import com.nowcoder.community.social.contracts.event.SocialContractEventCodec;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Objects;

@Service
public class SocialEventDispatchApplicationService {

    private final SocialContractEventCodec contractEventCodec;
    private final SocialIntegrationEventDispatcher dispatcher;

    public SocialEventDispatchApplicationService(
            SocialContractEventCodec contractEventCodec,
            SocialIntegrationEventDispatcher dispatcher
    ) {
        this.contractEventCodec = contractEventCodec;
        this.dispatcher = dispatcher;
    }

    public void dispatch(DispatchSocialEventCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        if (!StringUtils.hasText(command.payloadJson())) {
            throw new IllegalStateException("social event outbox payload is blank");
        }

        SocialContractEvent contractEvent = parseContractEvent(command.payloadJson());
        if (!StringUtils.hasText(contractEvent.eventId())) {
            throw new IllegalStateException("social event outbox payload missing eventId");
        }
        if (!StringUtils.hasText(contractEvent.type())) {
            throw new IllegalStateException("social event outbox payload missing type");
        }
        if (contractEvent.aggregateId() == null) {
            throw new IllegalStateException("social event outbox payload missing aggregateId");
        }
        if (!StringUtils.hasText(contractEvent.aggregateType())) {
            throw new IllegalStateException("social event outbox payload missing aggregateType");
        }
        if (contractEvent.occurredAt() == null) {
            throw new IllegalStateException("social event outbox payload missing occurredAt");
        }
        if (contractEvent.version() <= 0L) {
            throw new IllegalStateException("social event outbox payload missing version");
        }
        dispatcher.dispatch(command.eventKey(), contractEvent);
    }

    private SocialContractEvent parseContractEvent(String payloadJson) {
        try {
            SocialContractEvent envelope = contractEventCodec.deserialize(payloadJson);
            try {
                contractEventCodec.decode(envelope);
            } catch (IllegalArgumentException error) {
                throw new IllegalStateException(error.getMessage(), error);
            }
            return envelope;
        } catch (JsonCodecException e) {
            throw new IllegalStateException("social event outbox payload deserialization failed", e);
        }
    }
}
