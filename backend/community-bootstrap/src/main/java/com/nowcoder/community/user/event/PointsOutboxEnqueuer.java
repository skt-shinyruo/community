package com.nowcoder.community.user.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.content.api.event.ContentEventTypes;
import com.nowcoder.community.content.api.event.payload.CommentPayload;
import com.nowcoder.community.content.api.event.payload.PostPayload;
import com.nowcoder.community.content.event.ContentLocalEvent;
import com.nowcoder.community.infra.outbox.JdbcOutboxEventStore;
import com.nowcoder.community.social.api.event.SocialEventTypes;
import com.nowcoder.community.social.api.event.payload.LikePayload;
import com.nowcoder.community.social.event.SocialLocalEvent;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * BEFORE_COMMIT enqueuer for points projection.
 *
 * <p>Writes an outbox row in the same DB transaction so that points projection is retryable and does not affect HTTP responses.</p>
 */
@Component
@ConditionalOnProperty(prefix = "events.outbox", name = "enabled", havingValue = "true")
public class PointsOutboxEnqueuer {

    private static final String OUTBOX_EVENT_SUFFIX = ":points";

    private final ObjectMapper objectMapper;
    private final JdbcOutboxEventStore store;

    public PointsOutboxEnqueuer(ObjectMapper objectMapper, JdbcOutboxEventStore store) {
        this.objectMapper = objectMapper;
        this.store = store;
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT, fallbackExecution = false)
    public void onContentEvent(ContentLocalEvent event) {
        if (event == null) {
            return;
        }
        if (ContentEventTypes.POST_PUBLISHED.equals(event.type()) && event.payload() instanceof PostPayload payload) {
            enqueue(event.eventId(), event.type(), payload.getUserId(), 10);
            return;
        }
        if (ContentEventTypes.COMMENT_CREATED.equals(event.type()) && event.payload() instanceof CommentPayload payload) {
            enqueue(event.eventId(), event.type(), payload.getUserId(), 2);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT, fallbackExecution = false)
    public void onSocialEvent(SocialLocalEvent event) {
        if (event == null || !(event.payload() instanceof LikePayload payload)) {
            return;
        }

        int toUserId = payload.getEntityUserId() == null ? 0 : payload.getEntityUserId();
        if (toUserId <= 0 || toUserId == payload.getActorUserId()) {
            return;
        }

        if (SocialEventTypes.LIKE_CREATED.equals(event.type())) {
            enqueue(event.eventId(), event.type(), toUserId, 1);
            return;
        }
        if (SocialEventTypes.LIKE_REMOVED.equals(event.type())) {
            enqueue(event.eventId(), event.type(), toUserId, -1);
        }
    }

    private void enqueue(String sourceEventId, String sourceEventType, int userId, int delta) {
        if (userId <= 0 || delta == 0) {
            return;
        }
        if (sourceEventId == null || sourceEventId.isBlank()) {
            return;
        }
        if (sourceEventType == null || sourceEventType.isBlank()) {
            return;
        }

        PointsOutboxHandler.PointsOutboxPayload payload = new PointsOutboxHandler.PointsOutboxPayload();
        payload.setUserId(userId);
        payload.setDelta(delta);
        payload.setSourceEventId(sourceEventId);
        payload.setSourceEventType(sourceEventType);

        String payloadJson;
        try {
            payloadJson = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("points outbox payload 序列化失败", e);
        }

        store.enqueue(sourceEventId + OUTBOX_EVENT_SUFFIX, PointsOutboxHandler.TOPIC, String.valueOf(userId), payloadJson);
    }
}

