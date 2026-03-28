package com.nowcoder.community.search.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.content.api.model.PostScanView;
import com.nowcoder.community.content.api.query.PostScanQueryApi;
import com.nowcoder.community.content.event.payload.PostPayload;
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
    private final PostScanQueryApi postScanQueryApi;
    private final PostSearchRepository postSearchRepository;

    public PostOutboxHandler(
            ObjectMapper objectMapper,
            PostScanQueryApi postScanQueryApi,
            PostSearchRepository postSearchRepository
    ) {
        this.objectMapper = objectMapper;
        this.postScanQueryApi = postScanQueryApi;
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

        PostScanView.PostProjectionView projection = postScanQueryApi.getPostProjectionAllowDeleted(postId);
        if (projection == null || projection.postId() <= 0 || projection.status() == 2) {
            postSearchRepository.delete(postId);
            return;
        }

        postSearchRepository.upsert(toPostPayload(projection));
    }

    private PostPayload toPostPayload(PostScanView.PostProjectionView projection) {
        PostPayload payload = new PostPayload();
        payload.setPostId(projection.postId());
        payload.setUserId(projection.userId());
        payload.setCategoryId(projection.categoryId());
        payload.setTags(projection.tags());
        payload.setTitle(projection.title());
        payload.setContent(projection.content());
        payload.setType(projection.type());
        payload.setStatus(projection.status());
        payload.setCreateTime(projection.createTime());
        payload.setScore(projection.score());
        return payload;
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
