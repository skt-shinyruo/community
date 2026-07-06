package com.nowcoder.community.notice.infrastructure.event;

import com.fasterxml.jackson.databind.JsonNode;
import com.nowcoder.community.common.json.JsonCodec;
import com.nowcoder.community.content.contracts.event.CommentPayload;
import com.nowcoder.community.content.contracts.event.ContentContractEvent;
import com.nowcoder.community.content.contracts.event.ContentEventTypes;
import com.nowcoder.community.content.contracts.event.ModerationPayload;
import com.nowcoder.community.notice.application.NoticeProjectionApplicationService;
import com.nowcoder.community.notice.application.command.ProjectContentNoticeCommand;
import com.nowcoder.community.notice.application.command.ProjectSocialNoticeCommand;
import com.nowcoder.community.social.contracts.event.FollowPayload;
import com.nowcoder.community.social.contracts.event.LikePayload;
import com.nowcoder.community.social.contracts.event.SocialContractEvent;
import com.nowcoder.community.social.contracts.event.SocialEventTypes;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class NoticeProjectionKafkaListener {

    private final JsonCodec jsonCodec;
    private final NoticeProjectionApplicationService noticeProjectionApplicationService;

    public NoticeProjectionKafkaListener(
            JsonCodec jsonCodec,
            NoticeProjectionApplicationService noticeProjectionApplicationService
    ) {
        this.jsonCodec = jsonCodec;
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
        noticeProjectionApplicationService.projectContentEventReliably(new ProjectContentNoticeCommand(
                event.eventId(),
                event.version(),
                event.type(),
                normalizeContentPayload(event.type(), event.payload())
        ));
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
        noticeProjectionApplicationService.projectSocialEventReliably(new ProjectSocialNoticeCommand(
                event.eventId(),
                event.version(),
                event.type(),
                normalizeSocialPayload(event.type(), event.payload())
        ));
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

    private Object normalizeContentPayload(String type, Object payload) {
        if (ContentEventTypes.COMMENT_CREATED.equals(type)) {
            return normalizePayload(payload, CommentPayload.class);
        }
        if (ContentEventTypes.MODERATION_ACTION_APPLIED.equals(type)) {
            return normalizePayload(payload, ModerationPayload.class);
        }
        return payload;
    }

    private Object normalizeSocialPayload(String type, Object payload) {
        if (SocialEventTypes.LIKE_CREATED.equals(type) || SocialEventTypes.LIKE_REMOVED.equals(type)) {
            return normalizePayload(payload, LikePayload.class);
        }
        if (SocialEventTypes.FOLLOW_CREATED.equals(type)) {
            return normalizePayload(payload, FollowPayload.class);
        }
        return payload;
    }

    private <T> Object normalizePayload(Object payload, Class<T> type) {
        if (payload == null || type.isInstance(payload)) {
            return payload;
        }
        JsonNode node = jsonCodec.valueToTree(payload);
        return jsonCodec.treeToValue(node, type);
    }
}
