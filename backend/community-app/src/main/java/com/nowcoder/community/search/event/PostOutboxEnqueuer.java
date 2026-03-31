package com.nowcoder.community.search.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.content.contracts.event.ContentContractEvent;
import com.nowcoder.community.content.contracts.event.ContentEventTypes;
import com.nowcoder.community.content.contracts.event.PostPayload;
import com.nowcoder.community.common.outbox.JdbcOutboxEventStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * BEFORE_COMMIT enqueuer for search(post) projection.
 */
@Component
@ConditionalOnProperty(prefix = "events.outbox", name = "enabled", havingValue = "true")
public class PostOutboxEnqueuer {

    private static final String OUTBOX_EVENT_SUFFIX = ":search_post";

    private final ObjectMapper objectMapper;
    private final JdbcOutboxEventStore store;

    public PostOutboxEnqueuer(ObjectMapper objectMapper, JdbcOutboxEventStore store) {
        this.objectMapper = objectMapper;
        this.store = store;
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT, fallbackExecution = false)
    public void onContentEvent(ContentContractEvent event) {
        if (event == null) {
            return;
        }
        if (!(event.payload() instanceof PostPayload payload) || payload.getPostId() <= 0) {
            return;
        }

        if (!ContentEventTypes.POST_PUBLISHED.equals(event.type())
                && !ContentEventTypes.POST_UPDATED.equals(event.type())
                && !ContentEventTypes.POST_DELETED.equals(event.type())) {
            return;
        }

        PostOutboxHandler.PostOutboxPayload outboxPayload = new PostOutboxHandler.PostOutboxPayload();
        outboxPayload.setPostId(payload.getPostId());
        outboxPayload.setSourceEventId(event.eventId());
        outboxPayload.setSourceEventType(event.type());

        String payloadJson;
        try {
            payloadJson = objectMapper.writeValueAsString(outboxPayload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("search outbox payload 序列化失败: " + event.type(), e);
        }

        store.enqueue(event.eventId() + OUTBOX_EVENT_SUFFIX, PostOutboxHandler.TOPIC, String.valueOf(payload.getPostId()), payloadJson);
    }
}
