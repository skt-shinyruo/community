package com.nowcoder.community.message.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.content.event.ContentEventTypes;
import com.nowcoder.community.content.event.payload.CommentPayload;
import com.nowcoder.community.content.event.payload.ModerationPayload;
import com.nowcoder.community.content.event.ContentLocalEvent;
import com.nowcoder.community.infra.outbox.JdbcOutboxEventStore;
import com.nowcoder.community.social.event.SocialEventTypes;
import com.nowcoder.community.social.event.payload.FollowPayload;
import com.nowcoder.community.social.event.payload.LikePayload;
import com.nowcoder.community.social.event.SocialLocalEvent;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * BEFORE_COMMIT enqueuer for notice projection.
 */
@Component
@ConditionalOnProperty(prefix = "events.outbox", name = "enabled", havingValue = "true")
public class NoticeOutboxEnqueuer {

    private static final String OUTBOX_EVENT_SUFFIX = ":notice";

    private final ObjectMapper objectMapper;
    private final JdbcOutboxEventStore store;

    public NoticeOutboxEnqueuer(ObjectMapper objectMapper, JdbcOutboxEventStore store) {
        this.objectMapper = objectMapper;
        this.store = store;
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT, fallbackExecution = false)
    public void onContentEvent(ContentLocalEvent event) {
        if (event == null) {
            return;
        }

        if (ContentEventTypes.COMMENT_CREATED.equals(event.type()) && event.payload() instanceof CommentPayload payload) {
            Integer toUserId = payload.getTargetUserId();
            enqueue(event.eventId(), event.type(), "comment", toUserId, payload);
            return;
        }

        if (ContentEventTypes.MODERATION_ACTION_APPLIED.equals(event.type()) && event.payload() instanceof ModerationPayload payload) {
            Integer toUserId = payload.getToUserId();
            enqueue(event.eventId(), event.type(), "moderation", toUserId, payload);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT, fallbackExecution = false)
    public void onSocialEvent(SocialLocalEvent event) {
        if (event == null) {
            return;
        }

        if (SocialEventTypes.LIKE_CREATED.equals(event.type()) && event.payload() instanceof LikePayload payload) {
            Integer toUserId = payload.getEntityUserId();
            enqueue(event.eventId(), event.type(), "like", toUserId, payload);
            return;
        }

        if (SocialEventTypes.FOLLOW_CREATED.equals(event.type()) && event.payload() instanceof FollowPayload payload) {
            Integer toUserId = payload.getEntityUserId();
            enqueue(event.eventId(), event.type(), "follow", toUserId, payload);
        }
    }

    private void enqueue(String sourceEventId, String sourceEventType, String topic, Integer toUserId, Object payload) {
        if (toUserId == null || toUserId <= 0) {
            return;
        }
        if (sourceEventId == null || sourceEventId.isBlank()) {
            return;
        }
        if (sourceEventType == null || sourceEventType.isBlank()) {
            return;
        }
        if (topic == null || topic.isBlank()) {
            return;
        }

        NoticeOutboxHandler.NoticeOutboxPayload outboxPayload = new NoticeOutboxHandler.NoticeOutboxPayload();
        outboxPayload.setToUserId(toUserId);
        outboxPayload.setTopic(topic);
        outboxPayload.setSourceEventId(sourceEventId);
        outboxPayload.setSourceEventType(sourceEventType);
        outboxPayload.setPayload(objectMapper.valueToTree(payload));

        String payloadJson;
        try {
            payloadJson = objectMapper.writeValueAsString(outboxPayload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("notice outbox payload 序列化失败: " + sourceEventType, e);
        }

        store.enqueue(sourceEventId + OUTBOX_EVENT_SUFFIX, NoticeOutboxHandler.TOPIC, String.valueOf(toUserId), payloadJson);
    }
}

