package com.nowcoder.community.content.contracts.event;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.UUID;

public sealed interface ContentTypedEvent permits
        ContentTypedEvent.PostPublished,
        ContentTypedEvent.PostUpdated,
        ContentTypedEvent.PostDeleted,
        ContentTypedEvent.CommentCreated,
        ContentTypedEvent.CommentDeleted,
        ContentTypedEvent.ModerationActionApplied,
        ContentTypedEvent.Unknown {

    String eventId();

    UUID aggregateId();

    String aggregateType();

    Instant occurredAt();

    long version();

    record PostPublished(
            String eventId,
            UUID aggregateId,
            String aggregateType,
            Instant occurredAt,
            long version,
            PostPayload payload
    ) implements ContentTypedEvent {
    }

    record PostUpdated(
            String eventId,
            UUID aggregateId,
            String aggregateType,
            Instant occurredAt,
            long version,
            PostPayload payload
    ) implements ContentTypedEvent {
    }

    record PostDeleted(
            String eventId,
            UUID aggregateId,
            String aggregateType,
            Instant occurredAt,
            long version,
            PostPayload payload
    ) implements ContentTypedEvent {
    }

    record CommentCreated(
            String eventId,
            UUID aggregateId,
            String aggregateType,
            Instant occurredAt,
            long version,
            CommentPayload payload
    ) implements ContentTypedEvent {
    }

    record CommentDeleted(
            String eventId,
            UUID aggregateId,
            String aggregateType,
            Instant occurredAt,
            long version,
            CommentPayload payload
    ) implements ContentTypedEvent {
    }

    record ModerationActionApplied(
            String eventId,
            UUID aggregateId,
            String aggregateType,
            Instant occurredAt,
            long version,
            ModerationPayload payload
    ) implements ContentTypedEvent {
    }

    record Unknown(
            String eventId,
            UUID aggregateId,
            String aggregateType,
            String type,
            Instant occurredAt,
            long version,
            JsonNode payload
    ) implements ContentTypedEvent {
    }
}
