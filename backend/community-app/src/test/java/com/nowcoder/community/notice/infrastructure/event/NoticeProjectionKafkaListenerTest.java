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

import java.time.Instant;
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
    void contentKafkaListenerShouldProjectCommentCreatedReliably() {
        NoticeProjectionApplicationService applicationService = mock(NoticeProjectionApplicationService.class);
        NoticeProjectionKafkaListener listener = new NoticeProjectionKafkaListener(jsonCodec, applicationService);
        CommentPayload payload = commentPayload();

        listener.onContentEvent(contentEvent("evt-comment-1", ContentEventTypes.COMMENT_CREATED, 42L, payload));

        ArgumentCaptor<ProjectContentNoticeCommand> captor = ArgumentCaptor.forClass(ProjectContentNoticeCommand.class);
        verify(applicationService).projectContentEventReliably(captor.capture());
        assertThat(captor.getValue().sourceEventId()).isEqualTo("evt-comment-1");
        assertThat(captor.getValue().sourceVersion()).isEqualTo(42L);
        assertThat(captor.getValue().eventType()).isEqualTo(ContentEventTypes.COMMENT_CREATED);
        assertThat(captor.getValue().payload()).isSameAs(payload);
    }

    @Test
    void mapLikeContentPayloadShouldConvertBeforeDelegation() {
        NoticeProjectionApplicationService applicationService = mock(NoticeProjectionApplicationService.class);
        NoticeProjectionKafkaListener listener = new NoticeProjectionKafkaListener(jsonCodec, applicationService);

        listener.onContentEvent(contentEvent(
                "evt-comment-map",
                ContentEventTypes.COMMENT_CREATED,
                43L,
                Map.of("targetUserId", uuid(9).toString(), "postId", uuid(100).toString())
        ));

        ArgumentCaptor<ProjectContentNoticeCommand> captor = ArgumentCaptor.forClass(ProjectContentNoticeCommand.class);
        verify(applicationService).projectContentEventReliably(captor.capture());
        assertThat(captor.getValue().sourceEventId()).isEqualTo("evt-comment-map");
        assertThat(captor.getValue().sourceVersion()).isEqualTo(43L);
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

        listener.onSocialEvent(socialEvent(
                "evt-like-map",
                SocialEventTypes.LIKE_CREATED,
                44L,
                Map.of(
                        "actorUserId", uuid(1).toString(),
                        "entityType", EntityTypes.POST,
                        "entityId", uuid(100).toString(),
                        "entityUserId", uuid(2).toString(),
                        "relationKey", "like:" + uuid(1) + ":" + EntityTypes.POST + ":" + uuid(100)
                )
        ));

        ArgumentCaptor<ProjectSocialNoticeCommand> captor = ArgumentCaptor.forClass(ProjectSocialNoticeCommand.class);
        verify(applicationService).projectSocialEventReliably(captor.capture());
        assertThat(captor.getValue().sourceEventId()).isEqualTo("evt-like-map");
        assertThat(captor.getValue().sourceVersion()).isEqualTo(44L);
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

        listener.onContentEvent(new ContentContractEvent("evt-post-updated", null, null, ContentEventTypes.POST_UPDATED, java.time.Instant.EPOCH, 1L, Map.of("postId", uuid(100).toString())));
        listener.onContentEvent(null);
        listener.onSocialEvent(null);

        verifyNoInteractions(applicationService);
    }

    @Test
    void likeRemovedShouldBeDelegatedToReliableSocialProjection() {
        NoticeProjectionApplicationService applicationService = mock(NoticeProjectionApplicationService.class);
        NoticeProjectionKafkaListener listener = new NoticeProjectionKafkaListener(jsonCodec, applicationService);

        listener.onSocialEvent(socialEvent(
                "evt-like-removed",
                SocialEventTypes.LIKE_REMOVED,
                45L,
                Map.of(
                        "actorUserId", uuid(1).toString(),
                        "entityType", EntityTypes.POST,
                        "entityId", uuid(100).toString(),
                        "entityUserId", uuid(2).toString(),
                        "relationKey", "like:" + uuid(1) + ":" + EntityTypes.POST + ":" + uuid(100)
                )
        ));

        ArgumentCaptor<ProjectSocialNoticeCommand> captor = ArgumentCaptor.forClass(ProjectSocialNoticeCommand.class);
        verify(applicationService).projectSocialEventReliably(captor.capture());
        assertThat(captor.getValue().sourceVersion()).isEqualTo(45L);
        assertThat(captor.getValue().eventType()).isEqualTo(SocialEventTypes.LIKE_REMOVED);
        assertThat(captor.getValue().payload()).isInstanceOf(LikePayload.class);
        assertThat(((LikePayload) captor.getValue().payload()).getRelationKey())
                .isEqualTo("like:" + uuid(1) + ":" + EntityTypes.POST + ":" + uuid(100));
    }

    @Test
    void supportedNoticeEventsWithMissingBusinessIdentityShouldFailDelivery() {
        NoticeProjectionApplicationService applicationService = mock(NoticeProjectionApplicationService.class);
        NoticeProjectionKafkaListener listener = new NoticeProjectionKafkaListener(jsonCodec, applicationService);

        assertThatThrownBy(() -> listener.onContentEvent(new ContentContractEvent(
                "evt-comment-missing-target", null, null, ContentEventTypes.COMMENT_CREATED,
                Instant.EPOCH, 1L, Map.of("postId", uuid(100).toString()))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(ContentEventTypes.COMMENT_CREATED)
                .hasMessageContaining("evt-comment-missing-target");

        assertThatThrownBy(() -> listener.onSocialEvent(new SocialContractEvent(
                "evt-like-missing-owner", null, null, SocialEventTypes.LIKE_CREATED,
                Instant.EPOCH, 1L, Map.of(
                "actorUserId", uuid(1).toString(),
                "entityType", EntityTypes.POST,
                "entityId", uuid(100).toString()))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(SocialEventTypes.LIKE_CREATED)
                .hasMessageContaining("evt-like-missing-owner");

        verifyNoInteractions(applicationService);
    }

    @Test
    void supportedNoticeEventWithInvalidSourceMetadataShouldFailDelivery() {
        NoticeProjectionApplicationService applicationService = mock(NoticeProjectionApplicationService.class);
        NoticeProjectionKafkaListener listener = new NoticeProjectionKafkaListener(jsonCodec, applicationService);
        CommentPayload payload = commentPayload();

        assertThatThrownBy(() -> listener.onContentEvent(new ContentContractEvent(
                " ", null, null, ContentEventTypes.COMMENT_CREATED, null, 0L, payload)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(ContentEventTypes.COMMENT_CREATED);

        verifyNoInteractions(applicationService);
    }

    @Test
    void supportedLikeEventWithMissingRelationKeyShouldFailDelivery() {
        NoticeProjectionApplicationService applicationService = mock(NoticeProjectionApplicationService.class);
        NoticeProjectionKafkaListener listener = new NoticeProjectionKafkaListener(jsonCodec, applicationService);

        assertThatThrownBy(() -> listener.onSocialEvent(socialEvent(
                "evt-like-missing-relation", SocialEventTypes.LIKE_CREATED, 46L, Map.of(
                        "actorUserId", uuid(1).toString(),
                        "entityType", EntityTypes.POST,
                        "entityId", uuid(100).toString(),
                        "entityUserId", uuid(2).toString()))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(SocialEventTypes.LIKE_CREATED)
                .hasMessageContaining("evt-like-missing-relation");

        verifyNoInteractions(applicationService);
    }

    private static ContentContractEvent contentEvent(String eventId, String eventType, long version, Object payload) {
        return new ContentContractEvent(
                eventId,
                null,
                null,
                eventType,
                Instant.parse("2026-07-06T00:00:00Z"),
                version,
                payload
        );
    }

    private static SocialContractEvent socialEvent(String eventId, String eventType, long version, Object payload) {
        return new SocialContractEvent(
                eventId,
                null,
                null,
                eventType,
                Instant.parse("2026-07-06T00:00:00Z"),
                version,
                payload
        );
    }

    private static CommentPayload commentPayload() {
        CommentPayload payload = new CommentPayload();
        payload.setTargetUserId(uuid(9));
        return payload;
    }
}
