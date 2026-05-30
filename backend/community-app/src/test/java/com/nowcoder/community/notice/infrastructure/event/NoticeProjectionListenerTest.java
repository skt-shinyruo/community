package com.nowcoder.community.notice.infrastructure.event;

import com.fasterxml.jackson.databind.JsonNode;
import com.nowcoder.community.common.json.JacksonJsonCodec;
import com.nowcoder.community.common.json.JsonCodec;
import com.nowcoder.community.common.json.JsonMappers;
import com.nowcoder.community.content.contracts.event.CommentPayload;
import com.nowcoder.community.content.contracts.event.ContentContractEvent;
import com.nowcoder.community.content.contracts.event.ContentEventTypes;
import com.nowcoder.community.notice.application.NoticeApplicationService;
import com.nowcoder.community.notice.application.NoticePolicyProperties;
import com.nowcoder.community.notice.application.NoticeProjectionApplicationService;
import com.nowcoder.community.notice.application.command.CreateNoticeCommand;
import com.nowcoder.community.social.contracts.event.LikePayload;
import com.nowcoder.community.social.contracts.event.SocialContractEvent;
import com.nowcoder.community.social.contracts.event.SocialEventTypes;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class NoticeProjectionListenerTest {

    @Test
    void commentCreatedShouldCreateCommentNotice() {
        NoticeApplicationService noticeService = mock(NoticeApplicationService.class);
        NoticeProjectionListener listener = new NoticeProjectionListener(
                new NoticeProjectionApplicationService(jsonCodec(), noticeService)
        );
        UUID targetUserId = uuid(9);
        UUID postId = uuid(100);

        CommentPayload payload = new CommentPayload();
        payload.setTargetUserId(targetUserId);
        payload.setPostId(postId);

        listener.onContentEvent(new ContentContractEvent("evt-comment-1", ContentEventTypes.COMMENT_CREATED, payload));

        verify(noticeService).createNotice(argThat(command -> matches(command, targetUserId, "comment", "evt-comment-1")));
    }

    @Test
    void likeCreatedShouldCreateLikeNotice() {
        NoticeApplicationService noticeService = mock(NoticeApplicationService.class);
        NoticeProjectionListener listener = new NoticeProjectionListener(
                new NoticeProjectionApplicationService(jsonCodec(), noticeService)
        );
        UUID entityUserId = uuid(8);
        UUID entityId = uuid(200);

        LikePayload payload = new LikePayload();
        payload.setEntityUserId(entityUserId);
        payload.setEntityId(entityId);

        listener.onSocialEvent(new SocialContractEvent("evt-like-1", SocialEventTypes.LIKE_CREATED, payload));

        verify(noticeService).createNotice(argThat(command -> matches(command, entityUserId, "like", "evt-like-1")));
    }

    @Test
    void inAppDisabledShouldSuppressProjectedNoticeCreation() {
        NoticeApplicationService noticeService = mock(NoticeApplicationService.class);
        NoticePolicyProperties properties = new NoticePolicyProperties();
        properties.getChannels().setInAppEnabled(false);
        NoticeProjectionListener listener = new NoticeProjectionListener(
                new NoticeProjectionApplicationService(jsonCodec(), noticeService, properties)
        );
        UUID targetUserId = uuid(9);

        CommentPayload payload = new CommentPayload();
        payload.setTargetUserId(targetUserId);

        listener.onContentEvent(new ContentContractEvent("evt-comment-disabled", ContentEventTypes.COMMENT_CREATED, payload));

        verifyNoInteractions(noticeService);
    }

    private static JsonCodec jsonCodec() {
        return new JacksonJsonCodec(JsonMappers.standard());
    }

    private boolean matches(CreateNoticeCommand command, UUID toUserId, String topic, String eventId) {
        if (command == null) {
            return false;
        }
        JsonNode content = jsonCodec().readTree(command.contentJson());
        return toUserId.equals(command.toUserId())
                && topic.equals(command.topic())
                && eventId.equals(content.path("eventId").asText())
                && content.path("payload").isObject();
    }
}
