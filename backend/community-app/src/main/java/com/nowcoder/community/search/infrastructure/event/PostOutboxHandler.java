package com.nowcoder.community.search.infrastructure.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.common.outbox.OutboxEvent;
import com.nowcoder.community.common.outbox.OutboxHandler;
import com.nowcoder.community.search.application.SearchPostProjectionApplicationService;
import com.nowcoder.community.search.application.command.ProjectPostOutboxCommand;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.UUID;

/**
 * Outbox handler for search(post) projection.
 *
 * <p>To avoid out-of-order resurrecting deleted posts, we always project based on the current DB state.</p>
 */
@Component
@ConditionalOnProperty(prefix = "events.outbox", name = "enabled", havingValue = "true")
public class PostOutboxHandler implements OutboxHandler {

    private final ObjectMapper objectMapper;
    private final SearchPostProjectionApplicationService projectionApplicationService;
    private final String topic;

    public PostOutboxHandler(
            ObjectMapper objectMapper,
            SearchPostProjectionApplicationService projectionApplicationService,
            @Value("${search.outbox.post-topic:projection.search.post}") String topic
    ) {
        this.objectMapper = objectMapper;
        this.projectionApplicationService = projectionApplicationService;
        this.topic = topic;
    }

    @Override
    public String topic() {
        return topic;
    }

    @Override
    public void handle(OutboxEvent event) {
        if (event == null || !StringUtils.hasText(event.payload())) {
            return;
        }
        PostOutboxPayload payload;
        try {
            payload = objectMapper.readValue(event.payload(), PostOutboxPayload.class);
        } catch (Exception e) {
            throw new IllegalStateException("search outbox payload 反序列化失败", e);
        }

        if (payload.getPostId() == null) {
            return;
        }
        projectionApplicationService.projectPostFromOutbox(new ProjectPostOutboxCommand(
                payload.getPostId(),
                payload.getSourceEventId(),
                payload.getSourceEventType()
        ));
    }

    public static class PostOutboxPayload {

        private UUID postId;
        private String sourceEventId;
        private String sourceEventType;

        public UUID getPostId() {
            return postId;
        }

        public void setPostId(UUID postId) {
            this.postId = postId;
        }

        public String getSourceEventId() {
            return sourceEventId;
        }

        public void setSourceEventId(String sourceEventId) {
            this.sourceEventId = sourceEventId;
        }

        public String getSourceEventType() {
            return sourceEventType;
        }

        public void setSourceEventType(String sourceEventType) {
            this.sourceEventType = sourceEventType;
        }
    }
}
