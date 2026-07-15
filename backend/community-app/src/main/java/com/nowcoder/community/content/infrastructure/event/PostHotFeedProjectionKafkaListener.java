package com.nowcoder.community.content.infrastructure.event;

import com.nowcoder.community.common.constants.EntityTypes;
import com.nowcoder.community.content.application.PostHotFeedProjectionApplicationService;
import com.nowcoder.community.content.application.command.ProjectPostHotFeedCommand;
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

    private static final double POST_PUBLISHED_SIGNAL = 1.0;
    private static final double POST_UPDATED_SIGNAL = 1.0;
    private static final double POST_DELETED_SIGNAL = 0.0;
    private static final double COMMENT_CREATED_SIGNAL = 1.0;
    private static final double COMMENT_DELETED_SIGNAL = -1.0;
    private static final double LIKE_CREATED_SIGNAL = 1.0;
    private static final double LIKE_REMOVED_SIGNAL = -1.0;

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
        ProjectPostHotFeedCommand command = switch (event.type()) {
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
                || !EntityTypes.isValid(payload.getEntityType())
                || !StringUtils.hasText(payload.getRelationKey())) {
            throw malformed(event.type(), event.eventId());
        }
        if (payload.getEntityType() != EntityTypes.POST) {
            return;
        }
        UUID postId = payload.getPostId();
        if (postId == null) {
            throw malformed(event.type(), event.eventId());
        }
        applicationService.project(new ProjectPostHotFeedCommand(
                postId,
                null,
                SocialEventTypes.LIKE_CREATED.equals(event.type()) ? LIKE_CREATED_SIGNAL : LIKE_REMOVED_SIGNAL,
                event.eventId(),
                event.version()
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

    private ProjectPostHotFeedCommand commandForPostEvent(ContentContractEvent event, PostPayload payload) {
        if (payload == null || payload.getPostId() == null) {
            throw malformed(event.type(), event.eventId());
        }
        return new ProjectPostHotFeedCommand(
                payload.getPostId(),
                payload.getCategoryId(),
                switch (event.type()) {
                    case ContentEventTypes.POST_PUBLISHED -> POST_PUBLISHED_SIGNAL;
                    case ContentEventTypes.POST_UPDATED -> POST_UPDATED_SIGNAL;
                    default -> POST_DELETED_SIGNAL;
                },
                event.eventId(),
                event.version()
        );
    }

    private ProjectPostHotFeedCommand commandForCommentEvent(ContentContractEvent event, CommentPayload payload) {
        if (payload == null || payload.getPostId() == null) {
            throw malformed(event.type(), event.eventId());
        }
        return new ProjectPostHotFeedCommand(
                payload.getPostId(),
                null,
                ContentEventTypes.COMMENT_CREATED.equals(event.type())
                        ? COMMENT_CREATED_SIGNAL
                        : COMMENT_DELETED_SIGNAL,
                event.eventId(),
                event.version()
        );
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
}
