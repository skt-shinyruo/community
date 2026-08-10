package com.nowcoder.community.common.json.event;

import com.nowcoder.community.common.json.JsonCodecException;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

public final class OwnerEventDispatchSupport<E> {

    private final String owner;
    private final Function<String, E> deserializer;
    private final Consumer<E> typedPayloadValidator;
    private final Function<E, ? extends Metadata> metadataMapper;
    private final BiConsumer<String, E> dispatcher;
    private final boolean versioned;

    private OwnerEventDispatchSupport(
            String owner,
            Function<String, E> deserializer,
            Consumer<E> typedPayloadValidator,
            Function<E, ? extends Metadata> metadataMapper,
            BiConsumer<String, E> dispatcher,
            boolean versioned
    ) {
        this.owner = Objects.requireNonNull(owner, "owner must not be null");
        this.deserializer = Objects.requireNonNull(deserializer, "deserializer must not be null");
        this.typedPayloadValidator = Objects.requireNonNull(
                typedPayloadValidator,
                "typedPayloadValidator must not be null"
        );
        this.metadataMapper = Objects.requireNonNull(metadataMapper, "metadataMapper must not be null");
        this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher must not be null");
        this.versioned = versioned;
    }

    public static <E> OwnerEventDispatchSupport<E> basic(
            String owner,
            Function<String, E> deserializer,
            Consumer<E> typedPayloadValidator,
            Function<E, EnvelopeMetadata> metadataMapper,
            BiConsumer<String, E> dispatcher
    ) {
        return new OwnerEventDispatchSupport<>(
                owner,
                deserializer,
                typedPayloadValidator,
                metadataMapper,
                dispatcher,
                false
        );
    }

    public static <E> OwnerEventDispatchSupport<E> versioned(
            String owner,
            Function<String, E> deserializer,
            Consumer<E> typedPayloadValidator,
            Function<E, VersionedEnvelopeMetadata> metadataMapper,
            BiConsumer<String, E> dispatcher
    ) {
        return new OwnerEventDispatchSupport<>(
                owner,
                deserializer,
                typedPayloadValidator,
                metadataMapper,
                dispatcher,
                true
        );
    }

    public void dispatch(String eventKey, String payloadJson) {
        if (payloadJson == null || payloadJson.isBlank()) {
            throw new IllegalStateException(message("is blank"));
        }

        E event = decode(payloadJson);
        Metadata metadata = metadataMapper.apply(event);
        requireText(metadata.eventId(), "eventId");
        requireText(metadata.type(), "type");
        if (versioned) {
            validateVersioned((VersionedEnvelopeMetadata) metadata);
        }
        dispatcher.accept(eventKey, event);
    }

    private E decode(String payloadJson) {
        try {
            E event = deserializer.apply(payloadJson);
            try {
                typedPayloadValidator.accept(event);
            } catch (IllegalArgumentException error) {
                throw new IllegalStateException(error.getMessage(), error);
            }
            return event;
        } catch (JsonCodecException error) {
            throw new IllegalStateException(message("deserialization failed"), error);
        }
    }

    private void validateVersioned(VersionedEnvelopeMetadata metadata) {
        if (metadata.aggregateId() == null) {
            throw new IllegalStateException(message("missing aggregateId"));
        }
        requireText(metadata.aggregateType(), "aggregateType");
        if (metadata.occurredAt() == null) {
            throw new IllegalStateException(message("missing occurredAt"));
        }
        if (metadata.version() <= 0L) {
            throw new IllegalStateException(message("missing version"));
        }
    }

    private void requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(message("missing " + fieldName));
        }
    }

    private String message(String detail) {
        return owner + " event outbox payload " + detail;
    }

    private interface Metadata {

        String eventId();

        String type();
    }

    public record EnvelopeMetadata(String eventId, String type) implements Metadata {
    }

    public record VersionedEnvelopeMetadata(
            String eventId,
            String type,
            UUID aggregateId,
            String aggregateType,
            Instant occurredAt,
            long version
    ) implements Metadata {
    }
}
