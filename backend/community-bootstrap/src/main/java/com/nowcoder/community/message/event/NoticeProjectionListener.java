package com.nowcoder.community.message.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.content.api.event.ContentEventTypes;
import com.nowcoder.community.content.api.event.payload.CommentPayload;
import com.nowcoder.community.content.api.event.payload.ModerationPayload;
import com.nowcoder.community.content.event.ContentLocalEvent;
import com.nowcoder.community.message.service.NoticeService;
import com.nowcoder.community.social.api.event.SocialEventTypes;
import com.nowcoder.community.social.api.event.payload.FollowPayload;
import com.nowcoder.community.social.api.event.payload.LikePayload;
import com.nowcoder.community.social.event.SocialLocalEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Map;

@Component
public class NoticeProjectionListener {

    private final ObjectMapper objectMapper;
    private final NoticeService noticeService;

    public NoticeProjectionListener(ObjectMapper objectMapper, NoticeService noticeService) {
        this.objectMapper = objectMapper;
        this.noticeService = noticeService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = false)
    public void onContentEvent(ContentLocalEvent event) {
        if (event == null) {
            return;
        }
        if (ContentEventTypes.COMMENT_CREATED.equals(event.type()) && event.payload() instanceof CommentPayload payload) {
            createNotice(event.eventId(), event.type(), "comment", payload.getTargetUserId(), payload);
            return;
        }
        if (ContentEventTypes.MODERATION_ACTION_APPLIED.equals(event.type()) && event.payload() instanceof ModerationPayload payload) {
            createNotice(event.eventId(), event.type(), "moderation", payload.getToUserId(), payload);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = false)
    public void onSocialEvent(SocialLocalEvent event) {
        if (event == null) {
            return;
        }
        if (SocialEventTypes.LIKE_CREATED.equals(event.type()) && event.payload() instanceof LikePayload payload) {
            createNotice(event.eventId(), event.type(), "like", payload.getEntityUserId(), payload);
            return;
        }
        if (SocialEventTypes.FOLLOW_CREATED.equals(event.type()) && event.payload() instanceof FollowPayload payload) {
            createNotice(event.eventId(), event.type(), "follow", payload.getEntityUserId(), payload);
        }
    }

    private void createNotice(String eventId, String type, String topic, Integer toUserId, Object payload) {
        if (toUserId == null || toUserId <= 0) {
            return;
        }
        try {
            String contentJson = objectMapper.writeValueAsString(Map.of(
                    "eventId", eventId,
                    "type", type,
                    "payload", payload
            ));
            noticeService.createNotice(toUserId, topic, contentJson);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("notice payload 序列化失败: " + type, e);
        }
    }
}
