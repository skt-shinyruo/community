package com.nowcoder.community.wallet.infrastructure.event;

import com.nowcoder.community.common.constants.EntityTypes;
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
import com.nowcoder.community.wallet.application.WalletRewardProjectionApplicationService;
import com.nowcoder.community.wallet.application.command.RewardProjectionCommand;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Instant;

@Component
public class WalletRewardKafkaListener {

    private final ContentContractEventCodec contentContractEventCodec;
    private final SocialContractEventCodec socialContractEventCodec;
    private final WalletRewardProjectionApplicationService applicationService;

    public WalletRewardKafkaListener(
            ContentContractEventCodec contentContractEventCodec,
            SocialContractEventCodec socialContractEventCodec,
            WalletRewardProjectionApplicationService applicationService
    ) {
        this.contentContractEventCodec = contentContractEventCodec;
        this.socialContractEventCodec = socialContractEventCodec;
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
            groupId = "${user.reward.kafka.consumer.group-id:user-reward-projection}",
            concurrency = "${user.reward.kafka.consumer.concurrency:3}"
    )
    public void onSocialEvent(SocialContractEvent event) {
        if (event == null) {
            return;
        }
        if (SocialEventTypes.LIKE_CREATED.equals(event.type())) {
            requireSourceMetadata(event.eventId(), event.occurredAt(), event.version(), event.type());
            handleLikeCreated(event, ((SocialTypedEvent.LikeCreated) decodeSocial(event)).payload());
            return;
        }
        if (SocialEventTypes.LIKE_REMOVED.equals(event.type())) {
            requireSourceMetadata(event.eventId(), event.occurredAt(), event.version(), event.type());
            handleLikeRemoved(event, ((SocialTypedEvent.LikeRemoved) decodeSocial(event)).payload());
        }
    }

    private void handlePostPublished(ContentContractEvent event, PostPayload payload) {
        if (payload == null || payload.getPostId() == null || payload.getUserId() == null) {
            throw malformed(event.type(), event.eventId());
        }
        apply(applicationService.commandForPostPublished(payload.getPostId(), payload.getUserId()));
    }

    private void handleCommentCreated(ContentContractEvent event, CommentPayload payload) {
        if (payload == null || payload.getCommentId() == null || payload.getUserId() == null) {
            throw malformed(event.type(), event.eventId());
        }
        apply(applicationService.commandForCommentCreated(payload.getCommentId(), payload.getUserId()));
    }

    private void handleLikeCreated(SocialContractEvent event, LikePayload payload) {
        validateLikePayload(event, payload);
        apply(applicationService.commandForLikeCreated(
                likeSourceId("created", payload), payload.getActorUserId(), payload.getEntityUserId()));
    }

    private void handleLikeRemoved(SocialContractEvent event, LikePayload payload) {
        validateLikePayload(event, payload);
        apply(applicationService.commandForLikeRemoved(
                likeSourceId("removed", payload), payload.getActorUserId(), payload.getEntityUserId()));
    }

    private void apply(RewardProjectionCommand command) {
        if (command != null) {
            applicationService.apply(command);
        }
    }

    private void validateLikePayload(SocialContractEvent event, LikePayload payload) {
        if (payload == null
                || payload.getActorUserId() == null
                || !EntityTypes.isValid(payload.getEntityType())
                || payload.getEntityId() == null
                || payload.getEntityUserId() == null
                || !StringUtils.hasText(payload.getRelationKey())) {
            throw malformed(event.type(), event.eventId());
        }
    }

    private void requireSourceMetadata(String eventId, Instant occurredAt, long version, String eventType) {
        if (!StringUtils.hasText(eventId) || occurredAt == null || version <= 0L) {
            throw malformed(eventType, eventId);
        }
    }

    private IllegalArgumentException malformed(String eventType, String eventId) {
        return new IllegalArgumentException("invalid recognized event: type=" + eventType + ", eventId=" + eventId);
    }

    private String likeSourceId(String action, LikePayload payload) {
        String base = payload.getRelationInstanceId() == null
                ? payload.getRelationKey().trim()
                : payload.getRelationInstanceId().toString();
        return base + ":" + action;
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
