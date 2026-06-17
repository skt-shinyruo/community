package com.nowcoder.community.notice.infrastructure.event;

import com.nowcoder.community.common.constants.EntityTypes;
import com.nowcoder.community.common.json.JacksonJsonCodec;
import com.nowcoder.community.common.json.JsonCodec;
import com.nowcoder.community.common.json.JsonMappers;
import com.nowcoder.community.content.contracts.event.CommentPayload;
import com.nowcoder.community.content.contracts.event.ContentContractEvent;
import com.nowcoder.community.content.contracts.event.ContentEventTypes;
import com.nowcoder.community.notice.application.NoticeProjectionApplicationService;
import com.nowcoder.community.notice.application.command.ProjectContentNoticeCommand;
import com.nowcoder.community.notice.application.command.ProjectSocialNoticeCommand;
import com.nowcoder.community.social.contracts.event.LikePayload;
import com.nowcoder.community.social.contracts.event.SocialContractEvent;
import com.nowcoder.community.social.contracts.event.SocialEventTypes;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class NoticeProjectionKafkaListenerTest {

    private final JsonCodec jsonCodec = new JacksonJsonCodec(JsonMappers.standard());

    @Test
    void typedContentEventShouldEnterReliableNoticeProjection() {
        NoticeProjectionApplicationService applicationService = mock(NoticeProjectionApplicationService.class);
        NoticeProjectionKafkaListener listener = new NoticeProjectionKafkaListener(jsonCodec, applicationService);
        CommentPayload payload = new CommentPayload();
        payload.setTargetUserId(uuid(9));

        listener.onContentEvent(new ContentContractEvent("evt-comment-typed", ContentEventTypes.COMMENT_CREATED, payload));

        ArgumentCaptor<ProjectContentNoticeCommand> captor = ArgumentCaptor.forClass(ProjectContentNoticeCommand.class);
        verify(applicationService).projectContentEventReliably(captor.capture());
        assertThat(captor.getValue().sourceEventId()).isEqualTo("evt-comment-typed");
        assertThat(captor.getValue().eventType()).isEqualTo(ContentEventTypes.COMMENT_CREATED);
        assertThat(captor.getValue().payload()).isSameAs(payload);
    }

    @Test
    void mapLikeContentPayloadShouldConvertBeforeDelegation() {
        NoticeProjectionApplicationService applicationService = mock(NoticeProjectionApplicationService.class);
        NoticeProjectionKafkaListener listener = new NoticeProjectionKafkaListener(jsonCodec, applicationService);

        listener.onContentEvent(new ContentContractEvent(
                "evt-comment-map",
                ContentEventTypes.COMMENT_CREATED,
                Map.of("targetUserId", uuid(9).toString(), "postId", uuid(100).toString())
        ));

        ArgumentCaptor<ProjectContentNoticeCommand> captor = ArgumentCaptor.forClass(ProjectContentNoticeCommand.class);
        verify(applicationService).projectContentEventReliably(captor.capture());
        assertThat(captor.getValue().sourceEventId()).isEqualTo("evt-comment-map");
        assertThat(captor.getValue().eventType()).isEqualTo(ContentEventTypes.COMMENT_CREATED);
        assertThat(captor.getValue().payload()).isInstanceOf(CommentPayload.class);
        CommentPayload payload = (CommentPayload) captor.getValue().payload();
        assertThat(payload.getTargetUserId()).isEqualTo(uuid(9));
        assertThat(payload.getPostId()).isEqualTo(uuid(100));
    }

    @Test
    void mapLikeSocialLikePayloadShouldConvertBeforeDelegation() {
        NoticeProjectionApplicationService applicationService = mock(NoticeProjectionApplicationService.class);
        NoticeProjectionKafkaListener listener = new NoticeProjectionKafkaListener(jsonCodec, applicationService);

        listener.onSocialEvent(new SocialContractEvent(
                "evt-like-map",
                SocialEventTypes.LIKE_CREATED,
                Map.of(
                        "actorUserId", uuid(1).toString(),
                        "entityType", EntityTypes.POST,
                        "entityId", uuid(100).toString(),
                        "entityUserId", uuid(2).toString()
                )
        ));

        ArgumentCaptor<ProjectSocialNoticeCommand> captor = ArgumentCaptor.forClass(ProjectSocialNoticeCommand.class);
        verify(applicationService).projectSocialEventReliably(captor.capture());
        assertThat(captor.getValue().sourceEventId()).isEqualTo("evt-like-map");
        assertThat(captor.getValue().eventType()).isEqualTo(SocialEventTypes.LIKE_CREATED);
        assertThat(captor.getValue().payload()).isInstanceOf(LikePayload.class);
        LikePayload payload = (LikePayload) captor.getValue().payload();
        assertThat(payload.getActorUserId()).isEqualTo(uuid(1));
        assertThat(payload.getEntityUserId()).isEqualTo(uuid(2));
    }

    @Test
    void unsupportedNoticeEventsShouldBeIgnoredByListener() {
        NoticeProjectionApplicationService applicationService = mock(NoticeProjectionApplicationService.class);
        NoticeProjectionKafkaListener listener = new NoticeProjectionKafkaListener(jsonCodec, applicationService);

        listener.onContentEvent(new ContentContractEvent("evt-post-updated", ContentEventTypes.POST_UPDATED, Map.of("postId", uuid(100).toString())));
        listener.onSocialEvent(new SocialContractEvent("evt-like-removed", SocialEventTypes.LIKE_REMOVED, Map.of(
                "actorUserId", uuid(1).toString(),
                "entityType", EntityTypes.POST,
                "entityId", uuid(100).toString(),
                "entityUserId", uuid(2).toString()
        )));
        listener.onContentEvent(null);
        listener.onSocialEvent(null);

        verifyNoInteractions(applicationService);
    }

    @Test
    void supportedNoticeEventWithMissingBusinessFieldsShouldDelegateForApplicationNoOp() {
        NoticeProjectionApplicationService applicationService = mock(NoticeProjectionApplicationService.class);
        NoticeProjectionKafkaListener listener = new NoticeProjectionKafkaListener(jsonCodec, applicationService);

        listener.onContentEvent(new ContentContractEvent(
                "evt-comment-missing-target",
                ContentEventTypes.COMMENT_CREATED,
                Map.of("postId", uuid(100).toString())
        ));
        listener.onSocialEvent(new SocialContractEvent(
                "evt-like-missing-owner",
                SocialEventTypes.LIKE_CREATED,
                Map.of(
                        "actorUserId", uuid(1).toString(),
                        "entityType", EntityTypes.POST,
                        "entityId", uuid(100).toString()
                )
        ));

        ArgumentCaptor<ProjectContentNoticeCommand> contentCaptor = ArgumentCaptor.forClass(ProjectContentNoticeCommand.class);
        ArgumentCaptor<ProjectSocialNoticeCommand> socialCaptor = ArgumentCaptor.forClass(ProjectSocialNoticeCommand.class);
        verify(applicationService).projectContentEventReliably(contentCaptor.capture());
        verify(applicationService).projectSocialEventReliably(socialCaptor.capture());
        assertThat(contentCaptor.getValue().payload()).isInstanceOf(CommentPayload.class);
        assertThat(((CommentPayload) contentCaptor.getValue().payload()).getTargetUserId()).isNull();
        assertThat(socialCaptor.getValue().payload()).isInstanceOf(LikePayload.class);
        assertThat(((LikePayload) socialCaptor.getValue().payload()).getEntityUserId()).isNull();
    }

    @Test
    void supportedNoticeEventWithBlankEventIdShouldRemainRetryVisible() {
        NoticeProjectionApplicationService applicationService =
                new NoticeProjectionApplicationService(jsonCodec, mock(com.nowcoder.community.notice.application.NoticeApplicationService.class));
        NoticeProjectionKafkaListener listener = new NoticeProjectionKafkaListener(jsonCodec, applicationService);
        CommentPayload payload = new CommentPayload();
        payload.setTargetUserId(uuid(9));

        assertThatThrownBy(() -> listener.onContentEvent(new ContentContractEvent(" ", ContentEventTypes.COMMENT_CREATED, payload)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("notice projection source event id is blank");
    }
}
