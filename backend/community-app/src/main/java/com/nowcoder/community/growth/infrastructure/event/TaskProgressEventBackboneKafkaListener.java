package com.nowcoder.community.growth.infrastructure.event;

import com.nowcoder.community.common.constants.EntityTypes;
import com.nowcoder.community.content.contracts.event.CommentPayload;
import com.nowcoder.community.content.contracts.event.ContentContractEvent;
import com.nowcoder.community.content.contracts.event.ContentContractEventCodec;
import com.nowcoder.community.content.contracts.event.ContentEventTypes;
import com.nowcoder.community.content.contracts.event.ContentTypedEvent;
import com.nowcoder.community.content.contracts.event.PostPayload;
import com.nowcoder.community.growth.application.TaskProgressApplicationService;
import com.nowcoder.community.growth.application.TaskProgressApplicationService.TriggerCommentCreatedCommand;
import com.nowcoder.community.growth.application.TaskProgressApplicationService.TriggerLikeCreatedCommand;
import com.nowcoder.community.growth.application.TaskProgressApplicationService.TriggerLikeRemovedCommand;
import com.nowcoder.community.growth.application.TaskProgressApplicationService.TriggerPostPublishedCommand;
import com.nowcoder.community.social.contracts.event.LikePayload;
import com.nowcoder.community.social.contracts.event.SocialContractEvent;
import com.nowcoder.community.social.contracts.event.SocialContractEventCodec;
import com.nowcoder.community.social.contracts.event.SocialEventTypes;
import com.nowcoder.community.social.contracts.event.SocialTypedEvent;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class TaskProgressEventBackboneKafkaListener {

    private static final long DURABLE_RELATION_VERSION_FLOOR = 1L << 62;

    private final ContentContractEventCodec contentContractEventCodec;
    private final SocialContractEventCodec socialContractEventCodec;
    private final TaskProgressApplicationService applicationService;

    public TaskProgressEventBackboneKafkaListener(
            ContentContractEventCodec contentContractEventCodec,
            SocialContractEventCodec socialContractEventCodec,
            TaskProgressApplicationService applicationService
    ) {
        this.contentContractEventCodec = contentContractEventCodec;
        this.socialContractEventCodec = socialContractEventCodec;
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
            handlePostPublished(event, ((ContentTypedEvent.PostPublished) decodeContent(event)).payload());
            return;
        }
        if (ContentEventTypes.COMMENT_CREATED.equals(event.type())) {
            requireSourceMetadata(event.eventId(), event.occurredAt(), event.version(), event.type());
            handleCommentCreated(event, ((ContentTypedEvent.CommentCreated) decodeContent(event)).payload());
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
        SocialTypedEvent typedEvent = decodeSocial(event);
        LikePayload payload = typedEvent instanceof SocialTypedEvent.LikeCreated value
                ? value.payload()
                : ((SocialTypedEvent.LikeRemoved) typedEvent).payload();
        if (SocialEventTypes.LIKE_REMOVED.equals(event.type())) {
            handleLikeRemoved(event, payload);
        } else {
            handleLikeCreated(event, payload);
        }
    }

    private void handlePostPublished(ContentContractEvent event, PostPayload payload) {
        if (payload == null || payload.getPostId() == null || payload.getUserId() == null || payload.getCreateTime() == null) {
            throw malformed(event.type(), event.eventId());
        }
        applicationService.triggerPostPublished(new TriggerPostPublishedCommand(
                payload.getPostId(),
                payload.getUserId(),
                payload.getCreateTime()
        ));
    }

    private void handleCommentCreated(ContentContractEvent event, CommentPayload payload) {
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
        if (!hasCanonicalLikeIdentity(payload) || payload.getEntityUserId() == null) {
            throw malformed(event.type(), event.eventId());
        }
        if (payload.getActorUserId().equals(payload.getEntityUserId())) {
            return;
        }
        long sourceVersion = likeSourceVersion(event, payload);
        applicationService.triggerLikeCreated(new TriggerLikeCreatedCommand(
                event.eventId().trim(),
                sourceVersion,
                payload.getRelationKey().trim(),
                payload.getRelationInstanceId(),
                payload.getActorUserId(),
                payload.getEntityUserId(),
                event.occurredAt()
        ));
    }

    private void handleLikeRemoved(SocialContractEvent event, LikePayload payload) {
        if (!hasCanonicalLikeIdentity(payload) || payload.getEntityUserId() == null) {
            throw malformed(event.type(), event.eventId());
        }
        long sourceVersion = likeSourceVersion(event, payload);
        applicationService.triggerLikeRemoved(new TriggerLikeRemovedCommand(
                event.eventId().trim(),
                sourceVersion,
                payload.getRelationKey().trim(),
                payload.getRelationInstanceId(),
                payload.getEntityUserId()
        ));
    }

    private long likeSourceVersion(SocialContractEvent event, LikePayload payload) {
        Long relationVersion = payload.getRelationVersion();
        if (relationVersion == null) {
            if (event.version() >= DURABLE_RELATION_VERSION_FLOOR) {
                throw malformed(event.type(), event.eventId());
            }
            return event.version();
        }
        if (relationVersion <= 0L
                || event.version() != relationVersion
                || payload.getRelationInstanceId() == null) {
            throw malformed(event.type(), event.eventId());
        }
        return relationVersion;
    }

    private boolean hasCanonicalLikeIdentity(LikePayload payload) {
        if (payload == null
                || payload.getActorUserId() == null
                || !EntityTypes.isValid(payload.getEntityType())
                || payload.getEntityId() == null
                || !hasText(payload.getRelationKey())) {
            return false;
        }
        String expectedRelationKey = "like:"
                + payload.getActorUserId()
                + ":" + payload.getEntityType()
                + ":" + payload.getEntityId();
        return expectedRelationKey.equals(payload.getRelationKey().trim());
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

    private ContentTypedEvent decodeContent(ContentContractEvent event) {
        try {
            return contentContractEventCodec.decode(event);
        } catch (RuntimeException error) {
            throw malformed(event.type(), event.eventId());
        }
    }

    private SocialTypedEvent decodeSocial(SocialContractEvent event) {
        try {
            return socialContractEventCodec.decode(event);
        } catch (RuntimeException error) {
            throw malformed(event.type(), event.eventId());
        }
    }
}
