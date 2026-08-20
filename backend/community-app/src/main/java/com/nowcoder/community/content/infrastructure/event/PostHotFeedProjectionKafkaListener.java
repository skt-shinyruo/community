package com.nowcoder.community.content.infrastructure.event;

import com.nowcoder.community.common.constants.EntityTypes;
import com.nowcoder.community.content.application.PostHotFeedProjectionApplicationService;
import com.nowcoder.community.content.application.PostProjectionVersionLane;
import com.nowcoder.community.content.contracts.event.CommentPayload;
import com.nowcoder.community.content.contracts.event.ContentContractEvent;
import com.nowcoder.community.content.contracts.event.ContentContractEventCodec;
import com.nowcoder.community.content.contracts.event.ContentEventTypes;
import com.nowcoder.community.content.contracts.event.ContentTypedEvent;
import com.nowcoder.community.content.contracts.event.PostPayload;
import com.nowcoder.community.social.contracts.event.LikePayload;
import com.nowcoder.community.social.contracts.event.SocialContractEvent;
import com.nowcoder.community.social.contracts.event.SocialContractEventCodec;
import com.nowcoder.community.social.contracts.event.SocialEventTypes;
import com.nowcoder.community.social.contracts.event.SocialTypedEvent;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.UUID;

@Component
public class PostHotFeedProjectionKafkaListener {

    private final ContentContractEventCodec contentContractEventCodec;
    private final SocialContractEventCodec socialContractEventCodec;
    private final PostHotFeedProjectionApplicationService applicationService;

    public PostHotFeedProjectionKafkaListener(
            ContentContractEventCodec contentContractEventCodec,
            SocialContractEventCodec socialContractEventCodec,
            PostHotFeedProjectionApplicationService applicationService
    ) {
        this.contentContractEventCodec = contentContractEventCodec;
        this.socialContractEventCodec = socialContractEventCodec;
        this.applicationService = applicationService;
    }

    @KafkaListener(
            topics = "${content.events.kafka-topic:content.events}",
            groupId = "${content.hot-feed.kafka.consumer.group-id:content-hot-feed}",
            concurrency = "${content.hot-feed.kafka.consumer.concurrency:3}"
    )
    public void onContentEvent(ContentContractEvent event) {
        if (event == null || event.type() == null) {
            return;
        }
        if (!isSupportedContentEvent(event.type())) {
            return;
        }
        requireSourceMetadata(event.eventId(), event.occurredAt(), event.version(), event.type());
        ContentTypedEvent typedEvent = decodeContent(event);
        PostHotFeedProjectionApplicationService.ProjectPostHotFeedCommand command = switch (event.type()) {
            case ContentEventTypes.POST_PUBLISHED, ContentEventTypes.POST_UPDATED, ContentEventTypes.POST_DELETED ->
                    commandForPostEvent(event, postPayload(typedEvent));
            case ContentEventTypes.COMMENT_CREATED, ContentEventTypes.COMMENT_DELETED ->
                    commandForCommentEvent(event, commentPayload(typedEvent));
            default -> null;
        };
        if (command != null) {
            applicationService.project(command);
        }
    }

    @KafkaListener(
            topics = "${social.events.kafka-topic:social.events}",
            groupId = "${content.hot-feed.kafka.consumer.group-id:content-hot-feed}",
            concurrency = "${content.hot-feed.kafka.consumer.concurrency:3}"
    )
    public void onSocialEvent(SocialContractEvent event) {
        if (event == null || event.type() == null) {
            return;
        }
        if (!SocialEventTypes.LIKE_CREATED.equals(event.type()) && !SocialEventTypes.LIKE_REMOVED.equals(event.type())) {
            return;
        }
        requireSourceMetadata(event.eventId(), event.occurredAt(), event.version(), event.type());
        SocialTypedEvent typedEvent = decodeSocial(event);
        LikePayload payload = typedEvent instanceof SocialTypedEvent.LikeCreated value
                ? value.payload()
                : ((SocialTypedEvent.LikeRemoved) typedEvent).payload();
        if (payload == null
                || !EntityTypes.isValid(payload.entityType())
                || !StringUtils.hasText(payload.relationKey())) {
            throw malformed(event.type(), event.eventId());
        }
        if (payload.entityType() != EntityTypes.POST) {
            return;
        }
        UUID postId = payload.postId();
        if (postId == null) {
            throw malformed(event.type(), event.eventId());
        }
        applicationService.project(new PostHotFeedProjectionApplicationService.ProjectPostHotFeedCommand(
                postId,
                null,
                event.eventId(),
                event.version(),
                PostProjectionVersionLane.SOCIAL,
                false
        ));
    }

    private boolean isSupportedContentEvent(String type) {
        return ContentEventTypes.POST_PUBLISHED.equals(type)
                || ContentEventTypes.POST_UPDATED.equals(type)
                || ContentEventTypes.POST_DELETED.equals(type)
                || ContentEventTypes.COMMENT_CREATED.equals(type)
                || ContentEventTypes.COMMENT_DELETED.equals(type);
    }

    private void requireSourceMetadata(String eventId, java.time.Instant occurredAt, long version, String eventType) {
        if (!StringUtils.hasText(eventId) || occurredAt == null || version <= 0L) {
            throw malformed(eventType, eventId);
        }
    }

    private IllegalArgumentException malformed(String eventType, String eventId) {
        return new IllegalArgumentException(
                "invalid recognized event: type=" + eventType + ", eventId=" + eventId);
    }

    private PostHotFeedProjectionApplicationService.ProjectPostHotFeedCommand commandForPostEvent(
            ContentContractEvent event,
            PostPayload payload
    ) {
        if (payload == null || payload.postId() == null) {
            throw malformed(event.type(), event.eventId());
        }
        if (!"post".equalsIgnoreCase(event.aggregateType()) || !payload.postId().equals(event.aggregateId())) {
            throw malformed(event.type(), event.eventId());
        }
        boolean terminalDeletion = ContentEventTypes.POST_DELETED.equals(event.type());
        PostVersionSource versionSource = postVersionSource(event, payload);
        return new PostHotFeedProjectionApplicationService.ProjectPostHotFeedCommand(
                payload.postId(),
                payload.categoryId(),
                event.eventId(),
                versionSource.version(),
                versionSource.lane(),
                terminalDeletion
        );
    }

    private PostHotFeedProjectionApplicationService.ProjectPostHotFeedCommand commandForCommentEvent(
            ContentContractEvent event,
            CommentPayload payload
    ) {
        if (payload == null || payload.postId() == null) {
            throw malformed(event.type(), event.eventId());
        }
        long postAggregateVersion = payload.postAggregateVersion();
        return new PostHotFeedProjectionApplicationService.ProjectPostHotFeedCommand(
                payload.postId(),
                null,
                event.eventId(),
                postAggregateVersion > 0L ? postAggregateVersion : event.version(),
                postAggregateVersion > 0L
                        ? PostProjectionVersionLane.POST
                        : PostProjectionVersionLane.COMMENT,
                false
        );
    }

    private PostVersionSource postVersionSource(ContentContractEvent event, PostPayload payload) {
        long aggregateVersion = payload.aggregateVersion();
        if (aggregateVersion <= 0L) {
            return new PostVersionSource(event.version(), PostProjectionVersionLane.LEGACY_POST);
        }
        if (aggregateVersion != event.version()) {
            throw malformed(event.type(), event.eventId());
        }
        return new PostVersionSource(aggregateVersion, PostProjectionVersionLane.POST);
    }

    private PostPayload postPayload(ContentTypedEvent event) {
        if (event instanceof ContentTypedEvent.PostPublished value) {
            return value.payload();
        }
        if (event instanceof ContentTypedEvent.PostUpdated value) {
            return value.payload();
        }
        return ((ContentTypedEvent.PostDeleted) event).payload();
    }

    private CommentPayload commentPayload(ContentTypedEvent event) {
        return event instanceof ContentTypedEvent.CommentCreated value
                ? value.payload()
                : ((ContentTypedEvent.CommentDeleted) event).payload();
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

    private record PostVersionSource(long version, PostProjectionVersionLane lane) {
    }
}
