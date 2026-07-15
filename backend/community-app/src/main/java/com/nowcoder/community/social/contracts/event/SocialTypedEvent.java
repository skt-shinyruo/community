package com.nowcoder.community.social.contracts.event;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.UUID;

public sealed interface SocialTypedEvent permits
        SocialTypedEvent.LikeCreated,
        SocialTypedEvent.LikeRemoved,
        SocialTypedEvent.FollowCreated,
        SocialTypedEvent.BlockRelationChanged,
        SocialTypedEvent.Unknown {

    String eventId();

    UUID aggregateId();

    String aggregateType();

    Instant occurredAt();

    long version();

    record LikeCreated(
            String eventId,
            UUID aggregateId,
            String aggregateType,
            Instant occurredAt,
            long version,
            LikePayload payload
    ) implements SocialTypedEvent {
    }

    record LikeRemoved(
            String eventId,
            UUID aggregateId,
            String aggregateType,
            Instant occurredAt,
            long version,
            LikePayload payload
    ) implements SocialTypedEvent {
    }

    record FollowCreated(
            String eventId,
            UUID aggregateId,
            String aggregateType,
            Instant occurredAt,
            long version,
            FollowPayload payload
    ) implements SocialTypedEvent {
    }

    record BlockRelationChanged(
            String eventId,
            UUID aggregateId,
            String aggregateType,
            Instant occurredAt,
            long version,
            BlockPayload payload
    ) implements SocialTypedEvent {
    }

    record Unknown(
            String eventId,
            UUID aggregateId,
            String aggregateType,
            String type,
            Instant occurredAt,
            long version,
            JsonNode payload
    ) implements SocialTypedEvent {
    }
}
