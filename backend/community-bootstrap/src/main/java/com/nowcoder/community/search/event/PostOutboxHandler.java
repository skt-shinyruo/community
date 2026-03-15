package com.nowcoder.community.search.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.content.application.PostScanApplicationService;
import com.nowcoder.community.content.api.event.payload.PostPayload;
import com.nowcoder.community.infra.outbox.OutboxEvent;
import com.nowcoder.community.infra.outbox.OutboxHandler;
import com.nowcoder.community.search.repo.PostSearchRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Outbox handler for search(post) projection.
 *
 * <p>To avoid out-of-order resurrecting deleted posts, we always project based on the current DB state.</p>
 */
@Component
@ConditionalOnProperty(prefix = "events.outbox", name = "enabled", havingValue = "true")
public class PostOutboxHandler implements OutboxHandler {

    public static final String TOPIC = "projection.search.post";

    private final ObjectMapper objectMapper;
    private final PostScanApplicationService postScanApplicationService;
    private final PostSearchRepository postSearchRepository;

    public PostOutboxHandler(
            ObjectMapper objectMapper,
            PostScanApplicationService postScanApplicationService,
            PostSearchRepository postSearchRepository
    ) {
        this.objectMapper = objectMapper;
        this.postScanApplicationService = postScanApplicationService;
        this.postSearchRepository = postSearchRepository;
    }

    @Override
    public String topic() {
        return TOPIC;
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

        int postId = payload.getPostId();
        if (postId <= 0) {
            return;
        }

        PostPayload doc = postScanApplicationService.getPostPayloadAllowDeleted(postId);
        if (doc == null || doc.getPostId() <= 0 || doc.getStatus() == 2) {
            postSearchRepository.delete(postId);
            return;
        }

        postSearchRepository.upsert(doc);
    }

    public static class PostOutboxPayload {

        private int postId;
        private String sourceEventId;
        private String sourceEventType;

        public int getPostId() {
            return postId;
        }

        public void setPostId(int postId) {
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
