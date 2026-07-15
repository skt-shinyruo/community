package com.nowcoder.community.content.infrastructure.event;

import com.nowcoder.community.common.json.JsonCodec;
import com.nowcoder.community.common.outbox.JdbcOutboxEventStore;
import com.nowcoder.community.content.application.PostMediaReferenceCommandPublisher;
import com.nowcoder.community.content.application.command.PostMediaReferenceCommand;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public class OutboxPostMediaReferenceCommandPublisher implements PostMediaReferenceCommandPublisher {

    private final JsonCodec jsonCodec;
    private final JdbcOutboxEventStore store;
    private final String topic;

    public OutboxPostMediaReferenceCommandPublisher(
            JsonCodec jsonCodec,
            JdbcOutboxEventStore store,
            @Value("${content.media.reference-command-topic:command.content.post-media-reference}") String topic
    ) {
        this.jsonCodec = Objects.requireNonNull(jsonCodec, "jsonCodec must not be null");
        this.store = Objects.requireNonNull(store, "store must not be null");
        this.topic = Objects.requireNonNull(topic, "topic must not be null");
    }

    @Override
    public void publish(PostMediaReferenceCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        store.enqueue(
                eventId(command),
                topic,
                command.assetId().toString(),
                jsonCodec.toJson(command)
        );
    }

    private String eventId(PostMediaReferenceCommand command) {
        return "content-media-reference:"
                + command.assetId()
                + ":"
                + command.operationVersion()
                + ":"
                + command.operation().name();
    }
}
