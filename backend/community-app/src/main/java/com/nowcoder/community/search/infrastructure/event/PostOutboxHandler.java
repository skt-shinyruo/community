package com.nowcoder.community.search.infrastructure.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.content.api.model.PostScanView;
import com.nowcoder.community.content.api.query.PostScanQueryApi;
import com.nowcoder.community.common.outbox.OutboxEvent;
import com.nowcoder.community.common.outbox.OutboxHandler;
import com.nowcoder.community.search.application.PostSearchPayloadMapper;
import com.nowcoder.community.search.application.SearchApplicationService;
import com.nowcoder.community.search.application.command.DeleteIndexedPostCommand;
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

    public static final String TOPIC = "projection.search.post";

    private final ObjectMapper objectMapper;
    private final PostScanQueryApi postScanQueryApi;
    private final SearchApplicationService searchApplicationService;

    public PostOutboxHandler(
            ObjectMapper objectMapper,
            PostScanQueryApi postScanQueryApi,
            SearchApplicationService searchApplicationService
    ) {
        this.objectMapper = objectMapper;
        this.postScanQueryApi = postScanQueryApi;
        this.searchApplicationService = searchApplicationService;
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

        UUID postId = payload.getPostId();
        if (postId == null) {
            return;
        }

        PostScanView.PostProjectionView projection = postScanQueryApi.getPostProjectionAllowDeleted(postId);
        if (projection == null || projection.postId() == null) {
            searchApplicationService.deletePost(new DeleteIndexedPostCommand(postId));
            return;
        }

        searchApplicationService.syncPostProjection(PostSearchPayloadMapper.toSyncCommand(projection));
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
