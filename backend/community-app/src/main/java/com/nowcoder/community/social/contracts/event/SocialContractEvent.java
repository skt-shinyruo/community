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
}
