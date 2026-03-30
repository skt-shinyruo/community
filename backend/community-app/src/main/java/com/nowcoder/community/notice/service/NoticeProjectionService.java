package com.nowcoder.community.notice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.content.contracts.event.CommentPayload;
import com.nowcoder.community.content.contracts.event.ContentContractEvent;
import com.nowcoder.community.content.contracts.event.ContentEventTypes;
import com.nowcoder.community.content.contracts.event.ModerationPayload;
import com.nowcoder.community.social.contracts.event.FollowPayload;
import com.nowcoder.community.social.contracts.event.LikePayload;
import com.nowcoder.community.social.contracts.event.SocialContractEvent;
import com.nowcoder.community.social.contracts.event.SocialEventTypes;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class NoticeProjectionService {

    private final ObjectMapper objectMapper;
    private final NoticeService noticeService;

    public NoticeProjectionService(ObjectMapper objectMapper, NoticeService noticeService) {
        this.objectMapper = objectMapper;
        this.noticeService = noticeService;
    }

    public NoticeProjectionCommand commandForContentEvent(ContentContractEvent event) {
        if (event == null) {
            return null;
        }
        if (ContentEventTypes.COMMENT_CREATED.equals(event.type()) && event.payload() instanceof CommentPayload payload) {
            return command(event.eventId(), event.type(), "comment", payload.getTargetUserId(), payload);
        }
        if (ContentEventTypes.MODERATION_ACTION_APPLIED.equals(event.type()) && event.payload() instanceof ModerationPayload payload) {
            return command(event.eventId(), event.type(), "moderation", payload.getToUserId(), payload);
        }
        return null;
    }

    public NoticeProjectionCommand commandForSocialEvent(SocialContractEvent event) {
        if (event == null) {
            return null;
        }
        if (SocialEventTypes.LIKE_CREATED.equals(event.type()) && event.payload() instanceof LikePayload payload) {
            return command(event.eventId(), event.type(), "like", payload.getEntityUserId(), payload);
        }
        if (SocialEventTypes.FOLLOW_CREATED.equals(event.type()) && event.payload() instanceof FollowPayload payload) {
            return command(event.eventId(), event.type(), "follow", payload.getEntityUserId(), payload);
        }
        return null;
    }

    public void project(NoticeProjectionCommand command) {
        if (command == null || command.toUserId() <= 0) {
            return;
        }
        try {
            String contentJson = objectMapper.writeValueAsString(Map.of(
                    "eventId", command.sourceEventId(),
                    "type", command.sourceEventType(),
                    "payload", command.payload()
            ));
            noticeService.createNotice(command.toUserId(), command.topic(), contentJson);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("notice payload 序列化失败: " + command.sourceEventType(), e);
        }
    }

    private NoticeProjectionCommand command(String eventId, String eventType, String topic, Integer toUserId, Object payload) {
        if (toUserId == null || toUserId <= 0) {
            return null;
        }
        return new NoticeProjectionCommand(
                toUserId,
                topic,
                eventId,
                eventType,
                objectMapper.valueToTree(payload)
        );
    }

    public record NoticeProjectionCommand(
            int toUserId,
            String topic,
            String sourceEventId,
            String sourceEventType,
            JsonNode payload
    ) {
    }
}
