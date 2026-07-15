package com.nowcoder.community.notice.infrastructure.event;

import com.nowcoder.community.common.constants.EntityTypes;
import com.nowcoder.community.content.contracts.event.CommentPayload;
import com.nowcoder.community.content.contracts.event.ContentContractEvent;
import com.nowcoder.community.content.contracts.event.ContentContractEventCodec;
import com.nowcoder.community.content.contracts.event.ContentEventTypes;
import com.nowcoder.community.content.contracts.event.ContentTypedEvent;
import com.nowcoder.community.content.contracts.event.ModerationPayload;
import com.nowcoder.community.notice.application.NoticeProjectionApplicationService;
import com.nowcoder.community.notice.application.command.ProjectNoticeCommand;
import com.nowcoder.community.social.contracts.event.FollowPayload;
import com.nowcoder.community.social.contracts.event.LikePayload;
import com.nowcoder.community.social.contracts.event.SocialContractEvent;
import com.nowcoder.community.social.contracts.event.SocialContractEventCodec;
import com.nowcoder.community.social.contracts.event.SocialEventTypes;
import com.nowcoder.community.social.contracts.event.SocialTypedEvent;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Instant;

@Component
public class NoticeProjectionKafkaListener {

    private final ContentContractEventCodec contentContractEventCodec;
    private final SocialContractEventCodec socialContractEventCodec;
    private final NoticeProjectionApplicationService noticeProjectionApplicationService;

    public NoticeProjectionKafkaListener(
            ContentContractEventCodec contentContractEventCodec,
            SocialContractEventCodec socialContractEventCodec,
            NoticeProjectionApplicationService noticeProjectionApplicationService
    ) {
        this.contentContractEventCodec = contentContractEventCodec;
        this.socialContractEventCodec = socialContractEventCodec;
        this.noticeProjectionApplicationService = noticeProjectionApplicationService;
    }

    @KafkaListener(
            topics = "${content.events.kafka-topic:content.events}",
            groupId = "${notice.kafka.consumer.group-id:notice-projection}",
            concurrency = "${notice.kafka.consumer.concurrency:3}"
    )
    public void onContentEvent(ContentContractEvent event) {
        if (event == null || !isSupportedContentNoticeEvent(event.type())) {
            return;
        }
        requireSourceMetadata(event.eventId(), event.occurredAt(), event.version(), event.type());
        ProjectNoticeCommand command = commandForContentEvent(event, decodeContent(event));
        if (command == null) {
            throw malformed(event.type(), event.eventId());
        }
        noticeProjectionApplicationService.projectReliably(command);
    }

    @KafkaListener(
            topics = "${social.events.kafka-topic:social.events}",
            groupId = "${notice.kafka.consumer.group-id:notice-projection}",
            concurrency = "${notice.kafka.consumer.concurrency:3}"
    )
    public void onSocialEvent(SocialContractEvent event) {
        if (event == null || !isSupportedSocialNoticeEvent(event.type())) {
            return;
        }
        requireSourceMetadata(event.eventId(), event.occurredAt(), event.version(), event.type());
        ProjectNoticeCommand command = commandForSocialEvent(event, decodeSocial(event));
        if (command == null) {
            throw malformed(event.type(), event.eventId());
        }
        noticeProjectionApplicationService.projectReliably(command);
    }

    private ProjectNoticeCommand commandForContentEvent(ContentContractEvent event, ContentTypedEvent typedEvent) {
        if (ContentEventTypes.COMMENT_CREATED.equals(event.type())) {
            CommentPayload payload = ((ContentTypedEvent.CommentCreated) typedEvent).payload();
            if (payload == null || payload.getTargetUserId() == null) {
                return null;
            }
            return new ProjectNoticeCommand.CommentCreated(
                    event.eventId(),
                    event.version(),
                    event.type(),
                    payload.getCommentId(),
                    payload.getPostId(),
                    payload.getUserId(),
                    payload.getEntityType(),
                    payload.getEntityId(),
                    payload.getTargetUserId(),
                    payload.getContent(),
                    payload.getCreateTime()
            );
        }
        if (ContentEventTypes.MODERATION_ACTION_APPLIED.equals(event.type())) {
            ModerationPayload payload = ((ContentTypedEvent.ModerationActionApplied) typedEvent).payload();
            if (payload == null || payload.getToUserId() == null) {
                return null;
            }
            return new ProjectNoticeCommand.ModerationApplied(
                    event.eventId(),
                    event.version(),
                    event.type(),
                    payload.getReportId(),
                    payload.getKind(),
                    payload.getToUserId(),
                    payload.getActorUserId(),
                    payload.getTargetType(),
                    payload.getTargetId(),
                    payload.getAction(),
                    payload.getReason(),
                    payload.getDurationSeconds(),
                    payload.getCreateTime()
            );
        }
        return null;
    }

    private ProjectNoticeCommand commandForSocialEvent(SocialContractEvent event, SocialTypedEvent typedEvent) {
        if (SocialEventTypes.LIKE_CREATED.equals(event.type()) || SocialEventTypes.LIKE_REMOVED.equals(event.type())) {
            LikePayload payload = typedEvent instanceof SocialTypedEvent.LikeCreated value
                    ? value.payload()
                    : ((SocialTypedEvent.LikeRemoved) typedEvent).payload();
            if (!isValid(payload)) {
                return null;
            }
            if (SocialEventTypes.LIKE_CREATED.equals(event.type())) {
                return new ProjectNoticeCommand.LikeCreated(
                        event.eventId(),
                        event.version(),
                        event.type(),
                        payload.getActorUserId(),
                        payload.getEntityType(),
                        payload.getEntityId(),
                        payload.getEntityUserId(),
                        payload.getPostId(),
                        payload.getRelationKey()
                );
            }
            return new ProjectNoticeCommand.LikeRemoved(
                    event.eventId(),
                    event.version(),
                    event.type(),
                    payload.getActorUserId(),
                    payload.getEntityType(),
                    payload.getEntityId(),
                    payload.getEntityUserId(),
                    payload.getPostId(),
                    payload.getRelationKey()
            );
        }
        if (SocialEventTypes.FOLLOW_CREATED.equals(event.type())) {
            FollowPayload payload = ((SocialTypedEvent.FollowCreated) typedEvent).payload();
            if (!isValid(payload)) {
                return null;
            }
            return new ProjectNoticeCommand.FollowCreated(
                    event.eventId(),
                    event.version(),
                    event.type(),
                    payload.getActorUserId(),
                    payload.getEntityType(),
                    payload.getEntityId(),
                    payload.getEntityUserId(),
                    payload.getCreateTime()
            );
        }
        return null;
    }

    private boolean isValid(LikePayload payload) {
        return payload != null
                && payload.getActorUserId() != null
                && EntityTypes.isValid(payload.getEntityType())
                && payload.getEntityId() != null
                && payload.getEntityUserId() != null
                && StringUtils.hasText(payload.getRelationKey());
    }

    private boolean isValid(FollowPayload payload) {
        return payload != null
                && payload.getActorUserId() != null
                && EntityTypes.isValid(payload.getEntityType())
                && payload.getEntityId() != null
                && payload.getEntityUserId() != null;
    }

    private void requireSourceMetadata(String eventId, Instant occurredAt, long version, String eventType) {
        if (!StringUtils.hasText(eventId) || occurredAt == null || version <= 0L) {
            throw malformed(eventType, eventId);
        }
    }

    private boolean isSupportedContentNoticeEvent(String type) {
        return ContentEventTypes.COMMENT_CREATED.equals(type)
                || ContentEventTypes.MODERATION_ACTION_APPLIED.equals(type);
    }

    private boolean isSupportedSocialNoticeEvent(String type) {
        return SocialEventTypes.LIKE_CREATED.equals(type)
                || SocialEventTypes.LIKE_REMOVED.equals(type)
                || SocialEventTypes.FOLLOW_CREATED.equals(type);
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

    private IllegalArgumentException malformed(String eventType, String eventId) {
        return new IllegalArgumentException(
                "invalid recognized event: type=" + eventType + ", eventId=" + eventId);
    }
}
