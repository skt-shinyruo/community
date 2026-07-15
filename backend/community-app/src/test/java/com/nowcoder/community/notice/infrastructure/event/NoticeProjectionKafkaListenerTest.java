package com.nowcoder.community.notice.infrastructure.event;

import com.nowcoder.community.common.constants.EntityTypes;
import com.nowcoder.community.common.json.JacksonJsonCodec;
import com.nowcoder.community.common.json.JsonCodec;
import com.nowcoder.community.common.json.JsonMappers;
import com.nowcoder.community.content.contracts.event.CommentPayload;
import com.nowcoder.community.content.contracts.event.ContentContractEvent;
import com.nowcoder.community.content.contracts.event.ContentContractEventCodec;
import com.nowcoder.community.content.contracts.event.ContentEventTypes;
import com.nowcoder.community.content.contracts.event.ModerationPayload;
import com.nowcoder.community.content.infrastructure.event.JacksonContentContractEventCodec;
import com.nowcoder.community.notice.application.NoticeProjectionApplicationService;
import com.nowcoder.community.notice.application.command.ProjectNoticeCommand;
import com.nowcoder.community.social.contracts.event.FollowPayload;
import com.nowcoder.community.social.contracts.event.LikePayload;
import com.nowcoder.community.social.contracts.event.SocialContractEvent;
import com.nowcoder.community.social.contracts.event.SocialContractEventCodec;
import com.nowcoder.community.social.contracts.event.SocialEventTypes;
import com.nowcoder.community.social.infrastructure.event.JacksonSocialContractEventCodec;
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
    private final ContentContractEventCodec contentContractEventCodec =
            new JacksonContentContractEventCodec(jsonCodec);
    private final SocialContractEventCodec socialContractEventCodec =
            new JacksonSocialContractEventCodec(jsonCodec);

    @Test
    void commentCreatedShouldBeConvertedToNoticeCommandAtListenerBoundary() {
        NoticeProjectionApplicationService applicationService = mock(NoticeProjectionApplicationService.class);
        NoticeProjectionKafkaListener listener = listener(applicationService);
        CommentPayload payload = commentPayload();

        listener.onContentEvent(contentEvent("evt-comment-1", ContentEventTypes.COMMENT_CREATED, 42L, payload));

        ProjectNoticeCommand command = capturedCommand(applicationService);
        assertThat(command).isInstanceOf(ProjectNoticeCommand.CommentCreated.class);
        ProjectNoticeCommand.CommentCreated comment = (ProjectNoticeCommand.CommentCreated) command;
        assertThat(comment.sourceEventId()).isEqualTo("evt-comment-1");
        assertThat(comment.sourceVersion()).isEqualTo(42L);
        assertThat(comment.sourceEventType()).isEqualTo(ContentEventTypes.COMMENT_CREATED);
        assertThat(comment.commentId()).isEqualTo(uuid(10));
        assertThat(comment.postId()).isEqualTo(uuid(100));
        assertThat(comment.targetUserId()).isEqualTo(uuid(9));
        assertThat(comment.content()).isEqualTo("hello");
    }

    @Test
    void mapLikeCommentPayloadShouldBeDecodedBeforeApplicationBoundary() {
        NoticeProjectionApplicationService applicationService = mock(NoticeProjectionApplicationService.class);
        NoticeProjectionKafkaListener listener = listener(applicationService);

        listener.onContentEvent(contentEvent(
                "evt-comment-map",
                ContentEventTypes.COMMENT_CREATED,
                43L,
                Map.of("commentId", uuid(10).toString(), "targetUserId", uuid(9).toString(), "postId", uuid(100).toString())
        ));

        ProjectNoticeCommand.CommentCreated command = (ProjectNoticeCommand.CommentCreated) capturedCommand(applicationService);
        assertThat(command.sourceEventId()).isEqualTo("evt-comment-map");
        assertThat(command.sourceVersion()).isEqualTo(43L);
        assertThat(command.targetUserId()).isEqualTo(uuid(9));
        assertThat(command.postId()).isEqualTo(uuid(100));
    }

    @Test
    void moderationEventShouldBeConvertedToNoticeOwnedCommand() {
        NoticeProjectionApplicationService applicationService = mock(NoticeProjectionApplicationService.class);
        NoticeProjectionKafkaListener listener = listener(applicationService);
        ModerationPayload payload = new ModerationPayload();
        payload.setReportId(uuid(20));
        payload.setKind("report");
        payload.setToUserId(uuid(9));
        payload.setActorUserId(uuid(1));
        payload.setTargetType(EntityTypes.POST);
        payload.setTargetId(uuid(100));
        payload.setAction("delete");
        payload.setReason("spam");
        payload.setDurationSeconds(60);
        payload.setCreateTime(Instant.parse("2026-07-06T01:00:00Z"));

        listener.onContentEvent(contentEvent(
                "evt-moderation-1", ContentEventTypes.MODERATION_ACTION_APPLIED, 44L, payload));

        ProjectNoticeCommand.ModerationApplied command =
                (ProjectNoticeCommand.ModerationApplied) capturedCommand(applicationService);
        assertThat(command.toUserId()).isEqualTo(uuid(9));
        assertThat(command.reportId()).isEqualTo(uuid(20));
        assertThat(command.reason()).isEqualTo("spam");
    }

    @Test
    void mapLikeLikePayloadShouldBeDecodedBeforeApplicationBoundary() {
        NoticeProjectionApplicationService applicationService = mock(NoticeProjectionApplicationService.class);
        NoticeProjectionKafkaListener listener = listener(applicationService);
        String relationKey = "like:" + uuid(1) + ":" + EntityTypes.POST + ":" + uuid(100);

        listener.onSocialEvent(socialEvent(
                "evt-like-map",
                SocialEventTypes.LIKE_CREATED,
                45L,
                Map.of(
                        "actorUserId", uuid(1).toString(),
                        "entityType", EntityTypes.POST,
                        "entityId", uuid(100).toString(),
                        "entityUserId", uuid(2).toString(),
                        "postId", uuid(100).toString(),
                        "relationKey", relationKey
                )
        ));

        ProjectNoticeCommand.LikeCreated command = (ProjectNoticeCommand.LikeCreated) capturedCommand(applicationService);
        assertThat(command.actorUserId()).isEqualTo(uuid(1));
        assertThat(command.entityUserId()).isEqualTo(uuid(2));
        assertThat(command.relationKey()).isEqualTo(relationKey);
    }

    @Test
    void followCreatedShouldBeConvertedToNoticeOwnedCommand() {
        NoticeProjectionApplicationService applicationService = mock(NoticeProjectionApplicationService.class);
        NoticeProjectionKafkaListener listener = listener(applicationService);
        FollowPayload payload = new FollowPayload();
        payload.setActorUserId(uuid(1));
        payload.setEntityType(EntityTypes.USER);
        payload.setEntityId(uuid(2));
        payload.setEntityUserId(uuid(2));
        payload.setCreateTime(Instant.parse("2026-07-06T02:00:00Z"));

        listener.onSocialEvent(socialEvent(
                "evt-follow-1", SocialEventTypes.FOLLOW_CREATED, 46L, payload));

        ProjectNoticeCommand.FollowCreated command = (ProjectNoticeCommand.FollowCreated) capturedCommand(applicationService);
        assertThat(command.actorUserId()).isEqualTo(uuid(1));
        assertThat(command.entityUserId()).isEqualTo(uuid(2));
        assertThat(command.createTime()).isEqualTo(Instant.parse("2026-07-06T02:00:00Z"));
    }

    @Test
    void likeRemovedShouldBeConvertedToDedicatedRevocationCommand() {
        NoticeProjectionApplicationService applicationService = mock(NoticeProjectionApplicationService.class);
        NoticeProjectionKafkaListener listener = listener(applicationService);
        String relationKey = "like:" + uuid(1) + ":" + EntityTypes.POST + ":" + uuid(100);

        listener.onSocialEvent(socialEvent(
                "evt-like-removed",
                SocialEventTypes.LIKE_REMOVED,
                47L,
                Map.of(
                        "actorUserId", uuid(1).toString(),
                        "entityType", EntityTypes.POST,
                        "entityId", uuid(100).toString(),
                        "entityUserId", uuid(2).toString(),
                        "relationKey", relationKey
                )
        ));

        ProjectNoticeCommand.LikeRemoved command = (ProjectNoticeCommand.LikeRemoved) capturedCommand(applicationService);
        assertThat(command.sourceVersion()).isEqualTo(47L);
        assertThat(command.entityUserId()).isEqualTo(uuid(2));
        assertThat(command.relationKey()).isEqualTo(relationKey);
    }

    @Test
    void unsupportedNoticeEventsAndNullEventsShouldBeIgnored() {
        NoticeProjectionApplicationService applicationService = mock(NoticeProjectionApplicationService.class);
        NoticeProjectionKafkaListener listener = listener(applicationService);

        listener.onContentEvent(new ContentContractEvent(
                "evt-post-updated", null, null, ContentEventTypes.POST_UPDATED,
                Instant.EPOCH, 1L, jsonCodec.valueToTree(Map.of("postId", uuid(100).toString()))));
        listener.onContentEvent(null);
        listener.onSocialEvent(null);

        verifyNoInteractions(applicationService);
    }

    @Test
    void recognizedEventsWithMissingBusinessIdentityShouldFailDelivery() {
        NoticeProjectionApplicationService applicationService = mock(NoticeProjectionApplicationService.class);
        NoticeProjectionKafkaListener listener = listener(applicationService);

        assertThatThrownBy(() -> listener.onContentEvent(new ContentContractEvent(
                "evt-comment-missing-target", null, null, ContentEventTypes.COMMENT_CREATED,
                Instant.EPOCH, 1L, jsonCodec.valueToTree(Map.of("postId", uuid(100).toString())))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(ContentEventTypes.COMMENT_CREATED)
                .hasMessageContaining("evt-comment-missing-target");

        assertThatThrownBy(() -> listener.onSocialEvent(new SocialContractEvent(
                "evt-like-missing-owner", null, null, SocialEventTypes.LIKE_CREATED,
                Instant.EPOCH, 1L, jsonCodec.valueToTree(Map.of(
                "actorUserId", uuid(1).toString(),
                "entityType", EntityTypes.POST,
                "entityId", uuid(100).toString())))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(SocialEventTypes.LIKE_CREATED)
                .hasMessageContaining("evt-like-missing-owner");

        verifyNoInteractions(applicationService);
    }

    @Test
    void recognizedEventWithInvalidSourceMetadataShouldFailDelivery() {
        NoticeProjectionApplicationService applicationService = mock(NoticeProjectionApplicationService.class);
        NoticeProjectionKafkaListener listener = listener(applicationService);

        assertThatThrownBy(() -> listener.onContentEvent(new ContentContractEvent(
                " ", null, null, ContentEventTypes.COMMENT_CREATED, null, 0L,
                jsonCodec.valueToTree(commentPayload()))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(ContentEventTypes.COMMENT_CREATED);

        verifyNoInteractions(applicationService);
    }

    @Test
    void likeEventWithMissingRelationKeyShouldFailDelivery() {
        NoticeProjectionApplicationService applicationService = mock(NoticeProjectionApplicationService.class);
        NoticeProjectionKafkaListener listener = listener(applicationService);

        assertThatThrownBy(() -> listener.onSocialEvent(socialEvent(
                "evt-like-missing-relation", SocialEventTypes.LIKE_CREATED, 48L, Map.of(
                        "actorUserId", uuid(1).toString(),
                        "entityType", EntityTypes.POST,
                        "entityId", uuid(100).toString(),
                        "entityUserId", uuid(2).toString()))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(SocialEventTypes.LIKE_CREATED)
                .hasMessageContaining("evt-like-missing-relation");

        verifyNoInteractions(applicationService);
    }

    private static ProjectNoticeCommand capturedCommand(NoticeProjectionApplicationService applicationService) {
        ArgumentCaptor<ProjectNoticeCommand> captor = ArgumentCaptor.forClass(ProjectNoticeCommand.class);
        verify(applicationService).projectReliably(captor.capture());
        return captor.getValue();
    }

    private NoticeProjectionKafkaListener listener(NoticeProjectionApplicationService applicationService) {
        return new NoticeProjectionKafkaListener(
                contentContractEventCodec, socialContractEventCodec, applicationService);
    }

    private ContentContractEvent contentEvent(String eventId, String eventType, long version, Object payload) {
        return new ContentContractEvent(
                eventId, null, null, eventType, Instant.parse("2026-07-06T00:00:00Z"), version,
                jsonCodec.valueToTree(payload));
    }

    private SocialContractEvent socialEvent(String eventId, String eventType, long version, Object payload) {
        return new SocialContractEvent(
                eventId, null, null, eventType, Instant.parse("2026-07-06T00:00:00Z"), version,
                jsonCodec.valueToTree(payload));
    }

    private static CommentPayload commentPayload() {
        CommentPayload payload = new CommentPayload();
        payload.setCommentId(uuid(10));
        payload.setPostId(uuid(100));
        payload.setUserId(uuid(1));
        payload.setEntityType(EntityTypes.POST);
        payload.setEntityId(uuid(100));
        payload.setTargetUserId(uuid(9));
        payload.setContent("hello");
        payload.setCreateTime(Instant.parse("2026-07-06T00:00:00Z"));
        return payload;
    }
}
