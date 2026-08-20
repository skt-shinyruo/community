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
        if (payload == null || payload.postId() == null || payload.userId() == null || payload.createTime() == null) {
            throw malformed(event.type(), event.eventId());
        }
        applicationService.triggerPostPublished(new TriggerPostPublishedCommand(
                payload.postId(),
                payload.userId(),
                payload.createTime()
        ));
    }

    private void handleCommentCreated(ContentContractEvent event, CommentPayload payload) {
        if (payload == null || payload.commentId() == null || payload.userId() == null || payload.createTime() == null) {
            throw malformed(event.type(), event.eventId());
        }
        applicationService.triggerCommentCreated(new TriggerCommentCreatedCommand(
                payload.commentId(),
                payload.userId(),
                payload.createTime()
        ));
    }

    private void handleLikeCreated(SocialContractEvent event, LikePayload payload) {
        if (!hasCanonicalLikeIdentity(payload) || payload.entityUserId() == null) {
            throw malformed(event.type(), event.eventId());
        }
        if (payload.actorUserId().equals(payload.entityUserId())) {
            return;
        }
        long sourceVersion = likeSourceVersion(event, payload);
        applicationService.triggerLikeCreated(new TriggerLikeCreatedCommand(
                event.eventId().trim(),
                sourceVersion,
                payload.relationKey().trim(),
                payload.relationInstanceId(),
                payload.actorUserId(),
                payload.entityUserId(),
                event.occurredAt()
        ));
    }

    private void handleLikeRemoved(SocialContractEvent event, LikePayload payload) {
        if (!hasCanonicalLikeIdentity(payload) || payload.entityUserId() == null) {
            throw malformed(event.type(), event.eventId());
        }
        long sourceVersion = likeSourceVersion(event, payload);
        applicationService.triggerLikeRemoved(new TriggerLikeRemovedCommand(
                event.eventId().trim(),
                sourceVersion,
                payload.relationKey().trim(),
                payload.relationInstanceId(),
                payload.entityUserId()
        ));
    }

    private long likeSourceVersion(SocialContractEvent event, LikePayload payload) {
        Long relationVersion = payload.relationVersion();
        if (relationVersion == null) {
            if (event.version() >= DURABLE_RELATION_VERSION_FLOOR) {
                throw malformed(event.type(), event.eventId());
            }
            return event.version();
        }
        if (relationVersion <= 0L
                || event.version() != relationVersion
                || payload.relationInstanceId() == null) {
            throw malformed(event.type(), event.eventId());
        }
        return relationVersion;
    }

    private boolean hasCanonicalLikeIdentity(LikePayload payload) {
        if (payload == null
                || payload.actorUserId() == null
                || !EntityTypes.isValid(payload.entityType())
                || payload.entityId() == null
                || !hasText(payload.relationKey())) {
            return false;
        }
        String expectedRelationKey = "like:"
                + payload.actorUserId()
                + ":" + payload.entityType()
                + ":" + payload.entityId();
        return expectedRelationKey.equals(payload.relationKey().trim());
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
