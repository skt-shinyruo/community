package com.nowcoder.community.social.contracts.event;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.UUID;

public record SocialContractEvent(
        String eventId,
        UUID aggregateId,
        String aggregateType,
        String type,
        Instant occurredAt,
        long version,
        JsonNode payload
) {
}
