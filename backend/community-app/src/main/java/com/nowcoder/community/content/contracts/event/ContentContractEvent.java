package com.nowcoder.community.content.contracts.event;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.UUID;

public record ContentContractEvent(
        String eventId,
        UUID aggregateId,
        String aggregateType,
        String type,
        Instant occurredAt,
        long version,
        JsonNode payload
) {
}
