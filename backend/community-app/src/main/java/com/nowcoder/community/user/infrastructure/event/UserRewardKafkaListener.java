package com.nowcoder.community.user.infrastructure.event;

import com.fasterxml.jackson.databind.JsonNode;
import com.nowcoder.community.common.constants.EntityTypes;
import com.nowcoder.community.common.json.JsonCodec;
import com.nowcoder.community.content.contracts.event.CommentPayload;
import com.nowcoder.community.content.contracts.event.ContentContractEvent;
import com.nowcoder.community.content.contracts.event.ContentEventTypes;
import com.nowcoder.community.content.contracts.event.PostPayload;
import com.nowcoder.community.social.contracts.event.LikePayload;
import com.nowcoder.community.social.contracts.event.SocialContractEvent;
import com.nowcoder.community.social.contracts.event.SocialEventTypes;
import com.nowcoder.community.user.application.UserRewardApplicationService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Instant;

@Component
public class UserRewardKafkaListener {

    private final JsonCodec jsonCodec;
    private final UserRewardApplicationService applicationService;

    public UserRewardKafkaListener(
            JsonCodec jsonCodec,
            UserRewardApplicationService applicationService
    ) {
        this.jsonCodec = jsonCodec;
        this.applicationService = applicationService;
    }

    @KafkaListener(
            topics = "${content.events.kafka-topic:content.events}",
            groupId = "${user.reward.kafka.consumer.group-id:user-reward-projection}",
            concurrency = "${user.reward.kafka.consumer.concurrency:3}"
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
            groupId = "${user.reward.kafka.consumer.group-id:user-reward-projection}",
            concurrency = "${user.reward.kafka.consumer.concurrency:3}"
    )
    public void onSocialEvent(SocialContractEvent event) {
        if (event == null) {
            return;
        }
        if (SocialEventTypes.LIKE_CREATED.equals(event.type())) {
            requireSourceMetadata(event.eventId(), event.occurredAt(), event.version(), event.type());
            handleLikeCreated(event);
            return;
        }
        if (SocialEventTypes.LIKE_REMOVED.equals(event.type())) {
            requireSourceMetadata(event.eventId(), event.occurredAt(), event.version(), event.type());
            handleLikeRemoved(event);
        }
    }

    private void handlePostPublished(ContentContractEvent event) {
        PostPayload payload = normalizePayload(event.payload(), PostPayload.class);
        if (payload == null || payload.getPostId() == null || payload.getUserId() == null) {
            throw malformed(event.type(), event.eventId());
        }
        applicationService.apply(applicationService.commandForPostPublished(
                payload.getPostId(),
                payload.getUserId()
        ));
    }

    private void handleCommentCreated(ContentContractEvent event) {
        CommentPayload payload = normalizePayload(event.payload(), CommentPayload.class);
        if (payload == null || payload.getCommentId() == null || payload.getUserId() == null) {
            throw malformed(event.type(), event.eventId());
        }
        applicationService.apply(applicationService.commandForCommentCreated(
                payload.getCommentId(),
                payload.getUserId()
        ));
    }

    private void handleLikeCreated(SocialContractEvent event) {
        LikePayload payload = requiredLikePayload(event);
        if (payload.getActorUserId().equals(payload.getEntityUserId())) {
            return;
        }
        applicationService.apply(applicationService.commandForLikeCreated(
                likeSourceId("created", payload),
                payload.getActorUserId(),
                payload.getEntityUserId()
        ));
    }

    private void handleLikeRemoved(SocialContractEvent event) {
        LikePayload payload = requiredLikePayload(event);
        if (payload.getActorUserId().equals(payload.getEntityUserId())) {
            return;
        }
        applicationService.apply(applicationService.commandForLikeRemoved(
                likeSourceId("removed", payload),
                payload.getActorUserId(),
                payload.getEntityUserId()
        ));
    }

    private LikePayload requiredLikePayload(SocialContractEvent event) {
        LikePayload payload = normalizePayload(event.payload(), LikePayload.class);
        if (payload == null
                || payload.getActorUserId() == null
                || !EntityTypes.isValid(payload.getEntityType())
                || payload.getEntityId() == null
                || payload.getEntityUserId() == null
                || !StringUtils.hasText(payload.getRelationKey())) {
            throw malformed(event.type(), event.eventId());
        }
        return payload;
    }

    private void requireSourceMetadata(String eventId, Instant occurredAt, long version, String eventType) {
        if (!StringUtils.hasText(eventId) || occurredAt == null || version <= 0L) {
            throw malformed(eventType, eventId);
        }
    }

    private IllegalArgumentException malformed(String eventType, String eventId) {
        return new IllegalArgumentException(
                "invalid recognized event: type=" + eventType + ", eventId=" + eventId);
    }

    private String likeSourceId(String action, LikePayload payload) {
        return payload.getRelationKey().trim() + ":" + action;
    }

    private <T> T normalizePayload(Object payload, Class<T> type) {
        if (payload == null || type.isInstance(payload)) {
            return type.cast(payload);
        }
        JsonNode node = jsonCodec.valueToTree(payload);
        return jsonCodec.treeToValue(node, type);
    }
}
