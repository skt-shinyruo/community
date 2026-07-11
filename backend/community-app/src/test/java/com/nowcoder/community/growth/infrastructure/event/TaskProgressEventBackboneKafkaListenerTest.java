package com.nowcoder.community.growth.infrastructure.event;

import com.nowcoder.community.common.json.JacksonJsonCodec;
import com.nowcoder.community.common.json.JsonCodec;
import com.nowcoder.community.common.json.JsonMappers;
import com.nowcoder.community.content.contracts.event.CommentPayload;
import com.nowcoder.community.content.contracts.event.ContentContractEvent;
import com.nowcoder.community.content.contracts.event.ContentEventTypes;
import com.nowcoder.community.content.contracts.event.PostPayload;
import com.nowcoder.community.growth.application.TaskProgressApplicationService;
import com.nowcoder.community.growth.application.command.TriggerCommentCreatedCommand;
import com.nowcoder.community.growth.application.command.TriggerLikeCreatedCommand;
import com.nowcoder.community.growth.application.command.TriggerLikeRemovedCommand;
import com.nowcoder.community.growth.application.command.TriggerPostPublishedCommand;
import com.nowcoder.community.social.contracts.event.LikePayload;
import com.nowcoder.community.social.contracts.event.SocialContractEvent;
import com.nowcoder.community.social.contracts.event.SocialEventTypes;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.Map;

import static com.nowcoder.community.common.constants.EntityTypes.POST;
import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class TaskProgressEventBackboneKafkaListenerTest {

    private final JsonCodec jsonCodec = new JacksonJsonCodec(JsonMappers.standard());

    @Test
    void postPublishedShouldEnterGrowthApplicationServiceFromBackbone() {
        TaskProgressApplicationService applicationService = mock(TaskProgressApplicationService.class);
        TaskProgressEventBackboneKafkaListener listener = new TaskProgressEventBackboneKafkaListener(jsonCodec, applicationService);
        PostPayload payload = new PostPayload();
        payload.setPostId(uuid(100));
        payload.setUserId(uuid(7));
        payload.setCreateTime(Instant.parse("2026-05-18T08:30:00Z"));

        listener.onContentEvent(new ContentContractEvent("ce:post:published:1", null, null, ContentEventTypes.POST_PUBLISHED, java.time.Instant.EPOCH, 1L, payload));

        verify(applicationService).triggerPostPublished(new TriggerPostPublishedCommand(
                uuid(100),
                uuid(7),
                Instant.parse("2026-05-18T08:30:00Z")
        ));
    }

    @Test
    void commentCreatedShouldEnterGrowthApplicationServiceFromBackbone() {
        TaskProgressApplicationService applicationService = mock(TaskProgressApplicationService.class);
        TaskProgressEventBackboneKafkaListener listener = new TaskProgressEventBackboneKafkaListener(jsonCodec, applicationService);
        CommentPayload payload = new CommentPayload();
        payload.setCommentId(uuid(200));
        payload.setUserId(uuid(3));
        payload.setCreateTime(Instant.parse("2026-05-18T09:30:00Z"));

        listener.onContentEvent(new ContentContractEvent("ce:comment:created:1", null, null, ContentEventTypes.COMMENT_CREATED, java.time.Instant.EPOCH, 1L, payload));

        verify(applicationService).triggerCommentCreated(new TriggerCommentCreatedCommand(
                uuid(200),
                uuid(3),
                Instant.parse("2026-05-18T09:30:00Z")
        ));
    }

    @Test
    void mapLikeContentPayloadShouldConvertBeforeDelegation() {
        TaskProgressApplicationService applicationService = mock(TaskProgressApplicationService.class);
        TaskProgressEventBackboneKafkaListener listener = new TaskProgressEventBackboneKafkaListener(jsonCodec, applicationService);

        listener.onContentEvent(new ContentContractEvent("ce:post:published:map", null, null, ContentEventTypes.POST_PUBLISHED, java.time.Instant.EPOCH, 1L, Map.of(
                        "postId", uuid(101).toString(),
                        "userId", uuid(8).toString(),
                        "createTime", "2026-05-18T08:31:00Z"
                )));

        verify(applicationService).triggerPostPublished(new TriggerPostPublishedCommand(
                uuid(101),
                uuid(8),
                Instant.parse("2026-05-18T08:31:00Z")
        ));
    }

    @Test
    void likeCreatedShouldUseStableRelationKeyAsGrowthSourceId() {
        TaskProgressApplicationService applicationService = mock(TaskProgressApplicationService.class);
        TaskProgressEventBackboneKafkaListener listener = new TaskProgressEventBackboneKafkaListener(jsonCodec, applicationService);
        String relationKey = "like:" + uuid(1) + ":" + POST + ":" + uuid(100);
        LikePayload payload = likePayload(uuid(1), uuid(100), uuid(2), Instant.parse("2026-05-18T10:30:00Z"));
        payload.setRelationKey(relationKey);

        listener.onSocialEvent(new SocialContractEvent("se:like:created:1", null, null, SocialEventTypes.LIKE_CREATED, java.time.Instant.EPOCH, 1L, payload));
        listener.onSocialEvent(new SocialContractEvent("se:like:created:2", null, null, SocialEventTypes.LIKE_CREATED, java.time.Instant.EPOCH, 1L, payload));

        ArgumentCaptor<TriggerLikeCreatedCommand> captor = ArgumentCaptor.forClass(TriggerLikeCreatedCommand.class);
        verify(applicationService, org.mockito.Mockito.times(2)).triggerLikeCreated(captor.capture());
        TriggerLikeCreatedCommand first = captor.getAllValues().get(0);
        TriggerLikeCreatedCommand second = captor.getAllValues().get(1);
        assertThat(first.sourceEventId()).isEqualTo(relationKey);
        assertThat(first.sourceEventId()).isEqualTo(second.sourceEventId());
        assertThat(first.actorUserId()).isEqualTo(uuid(1));
        assertThat(first.entityUserId()).isEqualTo(uuid(2));
        assertThat(first.createTime()).isEqualTo(Instant.parse("2026-05-18T10:30:00Z"));
    }

    @Test
    void likeCreatedWithoutRelationKeyShouldUseDedicatedKafkaFallbackSourceId() {
        TaskProgressApplicationService applicationService = mock(TaskProgressApplicationService.class);
        TaskProgressEventBackboneKafkaListener listener = new TaskProgressEventBackboneKafkaListener(jsonCodec, applicationService);
        LikePayload payload = likePayload(uuid(1), uuid(100), uuid(2), Instant.parse("2026-05-18T10:30:00Z"));

        listener.onSocialEvent(new SocialContractEvent("se:like:created:no-relation-key", null, null, SocialEventTypes.LIKE_CREATED, java.time.Instant.EPOCH, 1L, payload));

        ArgumentCaptor<TriggerLikeCreatedCommand> captor = ArgumentCaptor.forClass(TriggerLikeCreatedCommand.class);
        verify(applicationService).triggerLikeCreated(captor.capture());
        assertThat(captor.getValue().sourceEventId()).isEqualTo("like-created:" + uuid(1) + ":" + POST + ":" + uuid(100));
    }

    @Test
    void mapLikeSocialPayloadShouldConvertBeforeDelegation() {
        TaskProgressApplicationService applicationService = mock(TaskProgressApplicationService.class);
        TaskProgressEventBackboneKafkaListener listener = new TaskProgressEventBackboneKafkaListener(jsonCodec, applicationService);

        listener.onSocialEvent(new SocialContractEvent("se:like:created:map", null, null, SocialEventTypes.LIKE_CREATED, java.time.Instant.EPOCH, 1L, Map.of(
                        "actorUserId", uuid(1).toString(),
                        "entityType", POST,
                        "entityId", uuid(100).toString(),
                        "entityUserId", uuid(2).toString(),
                        "relationKey", "like:" + uuid(1) + ":" + POST + ":" + uuid(100),
                        "createTime", "2026-05-18T10:30:00Z"
                )));

        ArgumentCaptor<TriggerLikeCreatedCommand> captor = ArgumentCaptor.forClass(TriggerLikeCreatedCommand.class);
        verify(applicationService).triggerLikeCreated(captor.capture());
        assertThat(captor.getValue().sourceEventId()).isEqualTo("like:" + uuid(1) + ":" + POST + ":" + uuid(100));
        assertThat(captor.getValue().actorUserId()).isEqualTo(uuid(1));
        assertThat(captor.getValue().entityUserId()).isEqualTo(uuid(2));
        assertThat(captor.getValue().createTime()).isEqualTo(Instant.parse("2026-05-18T10:30:00Z"));
    }

    @Test
    void likeRemovedShouldTriggerRollbackByRelationKey() {
        TaskProgressApplicationService applicationService = mock(TaskProgressApplicationService.class);
        TaskProgressEventBackboneKafkaListener listener = new TaskProgressEventBackboneKafkaListener(jsonCodec, applicationService);
        LikePayload payload = likePayload(uuid(1), uuid(100), uuid(2), Instant.parse("2026-05-18T10:30:00Z"));
        payload.setRelationKey("like:" + uuid(1) + ":" + POST + ":" + uuid(100));

        listener.onSocialEvent(new SocialContractEvent("se:like:removed:1", null, null, SocialEventTypes.LIKE_REMOVED, java.time.Instant.EPOCH, 1L, payload));

        verify(applicationService).triggerLikeRemoved(new TriggerLikeRemovedCommand(
                "like:" + uuid(1) + ":" + POST + ":" + uuid(100),
                uuid(2)
        ));
    }

    @Test
    void unsupportedEventsShouldBeIgnored() {
        TaskProgressApplicationService applicationService = mock(TaskProgressApplicationService.class);
        TaskProgressEventBackboneKafkaListener listener = new TaskProgressEventBackboneKafkaListener(jsonCodec, applicationService);

        listener.onContentEvent(new ContentContractEvent("ce:post:updated", null, null, ContentEventTypes.POST_UPDATED, java.time.Instant.EPOCH, 1L, new PostPayload()));
        listener.onSocialEvent(new SocialContractEvent("se:follow:created", null, null, SocialEventTypes.FOLLOW_CREATED, java.time.Instant.EPOCH, 1L, new Object()));

        verifyNoInteractions(applicationService);
    }

    @Test
    void selfLikeShouldBeIgnored() {
        TaskProgressApplicationService applicationService = mock(TaskProgressApplicationService.class);
        TaskProgressEventBackboneKafkaListener listener = new TaskProgressEventBackboneKafkaListener(jsonCodec, applicationService);
        LikePayload payload = likePayload(uuid(1), uuid(100), uuid(1), Instant.parse("2026-05-18T10:30:00Z"));

        listener.onSocialEvent(new SocialContractEvent("se:like:created:self", null, null, SocialEventTypes.LIKE_CREATED, java.time.Instant.EPOCH, 1L, payload));

        verifyNoInteractions(applicationService);
    }

    @Test
    void recognizedEventsWithMissingRequiredPayloadFieldsShouldFailDelivery() {
        TaskProgressApplicationService applicationService = mock(TaskProgressApplicationService.class);
        TaskProgressEventBackboneKafkaListener listener = new TaskProgressEventBackboneKafkaListener(jsonCodec, applicationService);
        PostPayload postPayload = new PostPayload();
        postPayload.setPostId(uuid(100));
        CommentPayload commentPayload = new CommentPayload();
        commentPayload.setCommentId(uuid(200));
        LikePayload likePayload = likePayload(uuid(1), uuid(100), uuid(2), null);

        listener.onContentEvent(null);
        assertThatThrownBy(() -> listener.onContentEvent(new ContentContractEvent(
                "ce:post:published:missing-user", null, null, ContentEventTypes.POST_PUBLISHED,
                Instant.EPOCH, 1L, postPayload)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(ContentEventTypes.POST_PUBLISHED)
                .hasMessageContaining("ce:post:published:missing-user");
        assertThatThrownBy(() -> listener.onContentEvent(new ContentContractEvent(
                "ce:comment:created:missing-user", null, null, ContentEventTypes.COMMENT_CREATED,
                Instant.EPOCH, 1L, commentPayload)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(ContentEventTypes.COMMENT_CREATED)
                .hasMessageContaining("ce:comment:created:missing-user");
        assertThatThrownBy(() -> listener.onSocialEvent(new SocialContractEvent(
                "se:like:created:missing-time", null, null, SocialEventTypes.LIKE_CREATED,
                Instant.EPOCH, 1L, likePayload)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(SocialEventTypes.LIKE_CREATED)
                .hasMessageContaining("se:like:created:missing-time");

        verifyNoInteractions(applicationService);
    }

    @Test
    void recognizedEventsWithInvalidSourceMetadataShouldFailDelivery() {
        TaskProgressApplicationService applicationService = mock(TaskProgressApplicationService.class);
        TaskProgressEventBackboneKafkaListener listener = new TaskProgressEventBackboneKafkaListener(jsonCodec, applicationService);
        PostPayload postPayload = new PostPayload();
        postPayload.setPostId(uuid(100));
        postPayload.setUserId(uuid(7));
        postPayload.setCreateTime(Instant.parse("2026-05-18T08:30:00Z"));
        LikePayload likePayload = likePayload(
                uuid(1), uuid(100), uuid(2), Instant.parse("2026-05-18T10:30:00Z"));
        likePayload.setRelationKey("like:" + uuid(1) + ":" + POST + ":" + uuid(100));

        assertThatThrownBy(() -> listener.onContentEvent(new ContentContractEvent(
                " ", null, null, ContentEventTypes.POST_PUBLISHED, Instant.EPOCH, 1L, postPayload)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(ContentEventTypes.POST_PUBLISHED);
        assertThatThrownBy(() -> listener.onContentEvent(new ContentContractEvent(
                "ce:post:published:zero-version", null, null, ContentEventTypes.POST_PUBLISHED,
                Instant.EPOCH, 0L, postPayload)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ce:post:published:zero-version");
        assertThatThrownBy(() -> listener.onSocialEvent(new SocialContractEvent(
                "se:like:removed:missing-source-time", null, null, SocialEventTypes.LIKE_REMOVED,
                null, 1L, likePayload)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("se:like:removed:missing-source-time");

        verifyNoInteractions(applicationService);
    }

    private static LikePayload likePayload(java.util.UUID actorUserId, java.util.UUID entityId, java.util.UUID entityUserId, Instant createTime) {
        LikePayload payload = new LikePayload();
        payload.setActorUserId(actorUserId);
        payload.setEntityType(POST);
        payload.setEntityId(entityId);
        payload.setEntityUserId(entityUserId);
        payload.setCreateTime(createTime);
        return payload;
    }
}
