package com.nowcoder.community.social.contracts.event;

import java.time.Instant;
import java.util.UUID;

public record SocialContractEvent(
        String eventId,
        UUID aggregateId,
        String aggregateType,
        String type,
        Instant occurredAt,
        long version,
        Object payload
) {

    public SocialContractEvent(String eventId, String type, Object payload) {
        this(eventId, null, null, type, null, 0L, payload);
    }
}
