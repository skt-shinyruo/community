package com.nowcoder.community.growth.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.content.event.ContentEventTypes;
import com.nowcoder.community.content.event.ContentLocalEvent;
import com.nowcoder.community.content.event.payload.CommentPayload;
import com.nowcoder.community.content.event.payload.PostPayload;
import com.nowcoder.community.growth.event.payload.CheckInPayload;
import com.nowcoder.community.growth.service.GrowthBusinessTimeService;
import com.nowcoder.community.infra.outbox.JdbcOutboxEventStore;
import com.nowcoder.community.social.event.SocialEventTypes;
import com.nowcoder.community.social.event.SocialLocalEvent;
import com.nowcoder.community.social.event.payload.LikePayload;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDate;
import java.time.Instant;

@Component
@ConditionalOnProperty(prefix = "events.outbox", name = "enabled", havingValue = "true")
public class TaskProgressOutboxEnqueuer {

    private static final String OUTBOX_SUFFIX = ":task-progress";

    private final ObjectMapper objectMapper;
    private final JdbcOutboxEventStore store;
    private final GrowthBusinessTimeService growthBusinessTimeService;

    public TaskProgressOutboxEnqueuer(
            ObjectMapper objectMapper,
            JdbcOutboxEventStore store,
            GrowthBusinessTimeService growthBusinessTimeService
    ) {
        this.objectMapper = objectMapper;
        this.store = store;
        this.growthBusinessTimeService = growthBusinessTimeService;
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT, fallbackExecution = false)
    public void onContentEvent(ContentLocalEvent event) {
        if (event == null) {
            return;
        }
        if (ContentEventTypes.POST_PUBLISHED.equals(event.type()) && event.payload() instanceof PostPayload payload) {
            enqueue(payload.getUserId(), event.eventId(), event.type(), toDate(payload.getCreateTime()));
            return;
        }
        if (ContentEventTypes.COMMENT_CREATED.equals(event.type()) && event.payload() instanceof CommentPayload payload) {
            enqueue(payload.getUserId(), event.eventId(), event.type(), toDate(payload.getCreateTime()));
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT, fallbackExecution = false)
    public void onSocialEvent(SocialLocalEvent event) {
        if (event == null || !(event.payload() instanceof LikePayload payload)) {
            return;
        }
        int toUserId = payload.getEntityUserId() == null ? 0 : payload.getEntityUserId();
        if (!SocialEventTypes.LIKE_CREATED.equals(event.type()) || toUserId <= 0 || toUserId == payload.getActorUserId()) {
            return;
        }
        enqueue(toUserId, event.eventId(), event.type(), toDate(payload.getCreateTime()));
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT, fallbackExecution = false)
    public void onGrowthEvent(GrowthLocalEvent event) {
        if (event == null || !GrowthEventTypes.CHECK_IN_COMPLETED.equals(event.type()) || !(event.payload() instanceof CheckInPayload payload)) {
            return;
        }
        enqueue(payload.getUserId(), event.eventId(), event.type(), payload.getBizDate());
    }

    private void enqueue(int userId, String sourceEventId, String triggerEventType, LocalDate bizDate) {
        if (userId <= 0 || sourceEventId == null || sourceEventId.isBlank() || triggerEventType == null || triggerEventType.isBlank() || bizDate == null) {
            return;
        }
        TaskProgressOutboxHandler.TaskProgressOutboxPayload payload = new TaskProgressOutboxHandler.TaskProgressOutboxPayload();
        payload.setUserId(userId);
        payload.setTriggerEventType(triggerEventType);
        payload.setSourceEventId(sourceEventId);
        payload.setBizDate(bizDate);
        String json;
        try {
            json = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("task progress outbox payload 序列化失败", e);
        }
        store.enqueue(sourceEventId + OUTBOX_SUFFIX, TaskProgressOutboxHandler.TOPIC, String.valueOf(userId), json);
    }

    private LocalDate toDate(Instant instant) {
        return growthBusinessTimeService.dateOf(instant);
    }
}
