package com.nowcoder.community.content.infrastructure.event;

import com.fasterxml.jackson.databind.JsonNode;
import com.nowcoder.community.common.constants.EntityTypes;
import com.nowcoder.community.common.json.JsonCodec;
import com.nowcoder.community.content.application.PostHotFeedProjectionApplicationService;
import com.nowcoder.community.content.application.command.ProjectPostHotFeedCommand;
import com.nowcoder.community.content.contracts.event.CommentPayload;
import com.nowcoder.community.content.contracts.event.ContentContractEvent;
import com.nowcoder.community.content.contracts.event.ContentEventTypes;
import com.nowcoder.community.content.contracts.event.PostPayload;
import com.nowcoder.community.social.contracts.event.LikePayload;
import com.nowcoder.community.social.contracts.event.SocialContractEvent;
import com.nowcoder.community.social.contracts.event.SocialEventTypes;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

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

    private final JsonCodec jsonCodec;
    private final PostHotFeedProjectionApplicationService applicationService;

    public PostHotFeedProjectionKafkaListener(
            JsonCodec jsonCodec,
            PostHotFeedProjectionApplicationService applicationService
    ) {
        this.jsonCodec = jsonCodec;
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
        ProjectPostHotFeedCommand command = switch (event.type()) {
            case ContentEventTypes.POST_PUBLISHED, ContentEventTypes.POST_UPDATED, ContentEventTypes.POST_DELETED ->
                    commandForPostEvent(event);
            case ContentEventTypes.COMMENT_CREATED, ContentEventTypes.COMMENT_DELETED ->
                    commandForCommentEvent(event);
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
        LikePayload payload = normalizePayload(event.payload(), LikePayload.class);
        if (payload == null || payload.getEntityType() != EntityTypes.POST) {
            return;
        }
        UUID postId = payload.getPostId() != null ? payload.getPostId() : payload.getEntityId();
        if (postId == null) {
            return;
        }
        applicationService.project(new ProjectPostHotFeedCommand(
                event.eventId(),
                event.type(),
                postId,
                null,
                SocialEventTypes.LIKE_CREATED.equals(event.type()) ? LIKE_CREATED_SIGNAL : LIKE_REMOVED_SIGNAL
        ));
    }

    private ProjectPostHotFeedCommand commandForPostEvent(ContentContractEvent event) {
        PostPayload payload = normalizePayload(event.payload(), PostPayload.class);
        if (payload == null || payload.getPostId() == null) {
            return null;
        }
        return new ProjectPostHotFeedCommand(
                event.eventId(),
                event.type(),
                payload.getPostId(),
                payload.getCategoryId(),
                switch (event.type()) {
                    case ContentEventTypes.POST_PUBLISHED -> POST_PUBLISHED_SIGNAL;
                    case ContentEventTypes.POST_UPDATED -> POST_UPDATED_SIGNAL;
                    default -> POST_DELETED_SIGNAL;
                }
        );
    }

    private ProjectPostHotFeedCommand commandForCommentEvent(ContentContractEvent event) {
        CommentPayload payload = normalizePayload(event.payload(), CommentPayload.class);
        if (payload == null || payload.getPostId() == null) {
            return null;
        }
        return new ProjectPostHotFeedCommand(
                event.eventId(),
                event.type(),
                payload.getPostId(),
                null,
                ContentEventTypes.COMMENT_CREATED.equals(event.type())
                        ? COMMENT_CREATED_SIGNAL
                        : COMMENT_DELETED_SIGNAL
        );
    }

    private <T> T normalizePayload(Object payload, Class<T> type) {
        if (payload == null) {
            return null;
        }
        if (type.isInstance(payload)) {
            return type.cast(payload);
        }
        JsonNode node = jsonCodec.valueToTree(payload);
        return jsonCodec.treeToValue(node, type);
    }
}
