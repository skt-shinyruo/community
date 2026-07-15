package com.nowcoder.community.content.infrastructure.event;

import com.nowcoder.community.common.json.JsonCodec;
import com.nowcoder.community.common.outbox.OutboxEvent;
import com.nowcoder.community.common.outbox.OutboxHandler;
import com.nowcoder.community.content.application.PostMediaReferenceApplicationService;
import com.nowcoder.community.content.application.command.PostMediaReferenceCommand;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public class PostMediaReferenceOutboxHandler implements OutboxHandler {

    private final JsonCodec jsonCodec;
    private final PostMediaReferenceApplicationService applicationService;
    private final String topic;

    public PostMediaReferenceOutboxHandler(
            JsonCodec jsonCodec,
            PostMediaReferenceApplicationService applicationService,
            @Value("${content.media.reference-command-topic:command.content.post-media-reference}") String topic
    ) {
        this.jsonCodec = Objects.requireNonNull(jsonCodec, "jsonCodec must not be null");
        this.applicationService = Objects.requireNonNull(applicationService, "applicationService must not be null");
        this.topic = Objects.requireNonNull(topic, "topic must not be null");
    }

    @Override
    public String topic() {
        return topic;
    }

    @Override
    public void handle(OutboxEvent event) {
        if (event == null) {
            return;
        }
        applicationService.process(jsonCodec.fromJson(event.payload(), PostMediaReferenceCommand.class));
    }
}
