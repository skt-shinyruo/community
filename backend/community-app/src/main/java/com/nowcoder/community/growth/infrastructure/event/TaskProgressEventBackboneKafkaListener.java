package com.nowcoder.community.growth.infrastructure.event;

import com.fasterxml.jackson.databind.JsonNode;
import com.nowcoder.community.common.constants.EntityTypes;
import com.nowcoder.community.common.json.JsonCodec;
import com.nowcoder.community.content.contracts.event.CommentPayload;
import com.nowcoder.community.content.contracts.event.ContentContractEvent;
import com.nowcoder.community.content.contracts.event.ContentEventTypes;
import com.nowcoder.community.content.contracts.event.PostPayload;
import com.nowcoder.community.growth.application.TaskProgressApplicationService;
import com.nowcoder.community.growth.application.command.TriggerCommentCreatedCommand;
import com.nowcoder.community.growth.application.command.TriggerLikeCreatedCommand;
import com.nowcoder.community.growth.application.command.TriggerLikeRemovedCommand;
import com.nowcoder.community.growth.application.command.TriggerPostPublishedCommand;
import com.nowcoder.community.social.contracts.event.LikePayload;
import com.nowcoder.community.social.contracts.event.SocialContractEvent;
import com.nowcoder.community.social.contracts.event.SocialEventTypes;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class TaskProgressEventBackboneKafkaListener {

    private final JsonCodec jsonCodec;
    private final TaskProgressApplicationService applicationService;

    public TaskProgressEventBackboneKafkaListener(
            JsonCodec jsonCodec,
            TaskProgressApplicationService applicationService
    ) {
        this.jsonCodec = jsonCodec;
        this.applicationService = applicationService;
    }

    @KafkaListener(
            topics = "${content.events.kafka-topic:content.events}",
            groupId = "${growth.task.kafka.consumer.group-id:growth-task-progress}",
            concurrency = "${growth.task.kafka.consumer.concurrency:3}"
    )
    public void onContentEvent(ContentContractEvent event) {
        if (event == null) {
            return;
        }
        if (ContentEventTypes.POST_PUBLISHED.equals(event.type())) {
            requireSourceMetadata(event.eventId(), event.occurredAt(), event.version(), event.type());
            handlePostPublished(event);
            return;
        }
        if (ContentEventTypes.COMMENT_CREATED.equals(event.type())) {
            requireSourceMetadata(event.eventId(), event.occurredAt(), event.version(), event.type());
            handleCommentCreated(event);
        }
    }

    @KafkaListener(
            topics = "${social.events.kafka-topic:social.events}",
            groupId = "${growth.task.kafka.consumer.group-id:growth-task-progress}",
            concurrency = "${growth.task.kafka.consumer.concurrency:3}"
    )
    public void onSocialEvent(SocialContractEvent event) {
        if (event == null
                || (!SocialEventTypes.LIKE_CREATED.equals(event.type()) && !SocialEventTypes.LIKE_REMOVED.equals(event.type()))) {
            return;
        }
        requireSourceMetadata(event.eventId(), event.occurredAt(), event.version(), event.type());
        LikePayload payload = normalizePayload(event.payload(), LikePayload.class);
        if (SocialEventTypes.LIKE_REMOVED.equals(event.type())) {
            handleLikeRemoved(event, payload);
        } else {
            handleLikeCreated(event, payload);
        }
    }

    private void handlePostPublished(ContentContractEvent event) {
        PostPayload payload = normalizePayload(event.payload(), PostPayload.class);
        if (payload == null || payload.getPostId() == null || payload.getUserId() == null || payload.getCreateTime() == null) {
            throw malformed(event.type(), event.eventId());
        }
        applicationService.triggerPostPublished(new TriggerPostPublishedCommand(
                payload.getPostId(),
                payload.getUserId(),
                payload.getCreateTime()
        ));
    }

    private void handleCommentCreated(ContentContractEvent event) {
        CommentPayload payload = normalizePayload(event.payload(), CommentPayload.class);
        if (payload == null || payload.getCommentId() == null || payload.getUserId() == null || payload.getCreateTime() == null) {
            throw malformed(event.type(), event.eventId());
        }
        applicationService.triggerCommentCreated(new TriggerCommentCreatedCommand(
                payload.getCommentId(),
                payload.getUserId(),
                payload.getCreateTime()
        ));
    }

    private void handleLikeCreated(SocialContractEvent event, LikePayload payload) {
        if (payload == null
                || payload.getActorUserId() == null
                || !EntityTypes.isValid(payload.getEntityType())
                || payload.getEntityId() == null
                || payload.getEntityUserId() == null
                || !hasText(payload.getRelationKey())) {
            throw malformed(event.type(), event.eventId());
        }
        if (payload.getActorUserId().equals(payload.getEntityUserId())) {
            return;
        }
        applicationService.triggerLikeCreated(new TriggerLikeCreatedCommand(
                payload.getRelationKey().trim(),
                payload.getActorUserId(),
                payload.getEntityUserId(),
                event.occurredAt()
        ));
    }

    private void handleLikeRemoved(SocialContractEvent event, LikePayload payload) {
        if (payload == null || payload.getEntityUserId() == null || !hasText(payload.getRelationKey())) {
            throw malformed(event.type(), event.eventId());
        }
        applicationService.triggerLikeRemoved(new TriggerLikeRemovedCommand(
                payload.getRelationKey().trim(),
                payload.getEntityUserId()
        ));
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private void requireSourceMetadata(String eventId, Instant occurredAt, long version, String eventType) {
        if (!hasText(eventId) || occurredAt == null || version <= 0L) {
            throw malformed(eventType, eventId);
        }
    }

    private IllegalArgumentException malformed(String eventType, String eventId) {
        return new IllegalArgumentException(
                "invalid recognized Growth event: type=" + eventType + ", eventId=" + eventId);
    }

    private <T> T normalizePayload(Object payload, Class<T> type) {
        if (payload == null || type.isInstance(payload)) {
            return type.cast(payload);
        }
        JsonNode node = jsonCodec.valueToTree(payload);
        return jsonCodec.treeToValue(node, type);
    }
}
