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
            handlePostPublished(event.payload());
            return;
        }
        if (ContentEventTypes.COMMENT_CREATED.equals(event.type())) {
            handleCommentCreated(event.payload());
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
            handleLikeCreated(event.payload());
            return;
        }
        if (SocialEventTypes.LIKE_REMOVED.equals(event.type())) {
            handleLikeRemoved(event.payload());
        }
    }

    private void handlePostPublished(Object eventPayload) {
        PostPayload payload = normalizePayload(eventPayload, PostPayload.class);
        if (payload == null || payload.getPostId() == null || payload.getUserId() == null) {
            return;
        }
        applicationService.apply(applicationService.commandForPostPublished(
                payload.getPostId(),
                payload.getUserId()
        ));
    }

    private void handleCommentCreated(Object eventPayload) {
        CommentPayload payload = normalizePayload(eventPayload, CommentPayload.class);
        if (payload == null || payload.getCommentId() == null || payload.getUserId() == null) {
            return;
        }
        applicationService.apply(applicationService.commandForCommentCreated(
                payload.getCommentId(),
                payload.getUserId()
        ));
    }

    private void handleLikeCreated(Object eventPayload) {
        LikePayload payload = normalizePayload(eventPayload, LikePayload.class);
        if (isInvalidLikePayload(payload)) {
            return;
        }
        applicationService.apply(applicationService.commandForLikeCreated(
                likeSourceId("like-created", payload),
                payload.getActorUserId(),
                payload.getEntityUserId()
        ));
    }

    private void handleLikeRemoved(Object eventPayload) {
        LikePayload payload = normalizePayload(eventPayload, LikePayload.class);
        if (isInvalidLikePayload(payload)) {
            return;
        }
        applicationService.apply(applicationService.commandForLikeRemoved(
                likeSourceId("like-removed", payload),
                payload.getActorUserId(),
                payload.getEntityUserId()
        ));
    }

    private boolean isInvalidLikePayload(LikePayload payload) {
        return payload == null
                || payload.getActorUserId() == null
                || !EntityTypes.isValid(payload.getEntityType())
                || payload.getEntityId() == null
                || payload.getEntityUserId() == null
                || payload.getActorUserId().equals(payload.getEntityUserId());
    }

    private String likeSourceId(String action, LikePayload payload) {
        return action + ":" + dashless(payload.getActorUserId()) + ":" + payload.getEntityType() + ":" + dashless(payload.getEntityId());
    }

    private String dashless(java.util.UUID value) {
        return value.toString().replace("-", "");
    }

    private <T> T normalizePayload(Object payload, Class<T> type) {
        if (payload == null || type.isInstance(payload)) {
            return type.cast(payload);
        }
        JsonNode node = jsonCodec.valueToTree(payload);
        return jsonCodec.treeToValue(node, type);
    }
}
