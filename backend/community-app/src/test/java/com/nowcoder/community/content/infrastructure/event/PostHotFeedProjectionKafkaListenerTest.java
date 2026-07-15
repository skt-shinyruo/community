package com.nowcoder.community.content.infrastructure.event;

import com.nowcoder.community.common.constants.EntityTypes;
import com.nowcoder.community.common.json.JacksonJsonCodec;
import com.nowcoder.community.common.json.JsonCodec;
import com.nowcoder.community.common.json.JsonMappers;
import com.nowcoder.community.content.application.PostHotFeedProjectionApplicationService;
import com.nowcoder.community.content.application.command.ProjectPostHotFeedCommand;
import com.nowcoder.community.content.contracts.event.ContentContractEvent;
import com.nowcoder.community.content.contracts.event.ContentContractEventCodec;
import com.nowcoder.community.content.contracts.event.ContentEventTypes;
import com.nowcoder.community.content.contracts.event.PostPayload;
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

class PostHotFeedProjectionKafkaListenerTest {

    private final JsonCodec jsonCodec = new JacksonJsonCodec(JsonMappers.standard());
    private final ContentContractEventCodec contentContractEventCodec =
            new JacksonContentContractEventCodec(jsonCodec);
    private final SocialContractEventCodec socialContractEventCodec =
            new JacksonSocialContractEventCodec(jsonCodec);

    @Test
    void postPublishedShouldMapTypedContentPayloadToProjectionCommand() {
        PostHotFeedProjectionApplicationService applicationService = mock(PostHotFeedProjectionApplicationService.class);
        PostHotFeedProjectionKafkaListener listener = listener(applicationService);

        listener.onContentEvent(new ContentContractEvent(
                "evt-post-published",
                uuid(200),
                "post",
                ContentEventTypes.POST_PUBLISHED,
                Instant.parse("2026-07-06T08:00:00Z"),
                42L,
                jsonCodec.valueToTree(postPayload(uuid(200), uuid(10)))
        ));

        ArgumentCaptor<ProjectPostHotFeedCommand> captor = ArgumentCaptor.forClass(ProjectPostHotFeedCommand.class);
        verify(applicationService).project(captor.capture());
        assertThat(captor.getValue().sourceEventId()).isEqualTo("evt-post-published");
        assertThat(captor.getValue().sourceVersion()).isEqualTo(42L);
        assertThat(captor.getValue().postId()).isEqualTo(uuid(200));
        assertThat(captor.getValue().boardId()).isEqualTo(uuid(10));
    }

    @Test
    void contentMapPayloadShouldConvertBeforeDelegation() {
        PostHotFeedProjectionApplicationService applicationService = mock(PostHotFeedProjectionApplicationService.class);
        PostHotFeedProjectionKafkaListener listener = listener(applicationService);

        listener.onContentEvent(new ContentContractEvent(
                "evt-post-map",
                uuid(201),
                "post",
                ContentEventTypes.POST_UPDATED,
                Instant.parse("2026-07-06T08:01:00Z"),
                43L,
                jsonCodec.valueToTree(Map.of("postId", uuid(201).toString(), "categoryId", uuid(11).toString()))
        ));

        ArgumentCaptor<ProjectPostHotFeedCommand> captor = ArgumentCaptor.forClass(ProjectPostHotFeedCommand.class);
        verify(applicationService).project(captor.capture());
        assertThat(captor.getValue().postId()).isEqualTo(uuid(201));
        assertThat(captor.getValue().boardId()).isEqualTo(uuid(11));
        assertThat(captor.getValue().sourceVersion()).isEqualTo(43L);
    }

    @Test
    void postLikeCreatedShouldMapSocialPayloadToProjectionCommand() {
        PostHotFeedProjectionApplicationService applicationService = mock(PostHotFeedProjectionApplicationService.class);
        PostHotFeedProjectionKafkaListener listener = listener(applicationService);

        listener.onSocialEvent(new SocialContractEvent(
                "evt-like-created",
                uuid(202),
                "like",
                SocialEventTypes.LIKE_CREATED,
                Instant.parse("2026-07-06T08:02:00Z"),
                44L,
                jsonCodec.valueToTree(likePayload(EntityTypes.POST, uuid(202)))
        ));

        ArgumentCaptor<ProjectPostHotFeedCommand> captor = ArgumentCaptor.forClass(ProjectPostHotFeedCommand.class);
        verify(applicationService).project(captor.capture());
        assertThat(captor.getValue().sourceEventId()).isEqualTo("evt-like-created");
        assertThat(captor.getValue().sourceVersion()).isEqualTo(44L);
        assertThat(captor.getValue().postId()).isEqualTo(uuid(202));
        assertThat(captor.getValue().boardId()).isNull();
        assertThat(captor.getValue().signalWeight()).isEqualTo(1.0);
    }

    @Test
    void nonPostSocialSignalsShouldBeIgnored() {
        PostHotFeedProjectionApplicationService applicationService = mock(PostHotFeedProjectionApplicationService.class);
        PostHotFeedProjectionKafkaListener listener = listener(applicationService);

        listener.onSocialEvent(new SocialContractEvent(
                "evt-like-comment",
                uuid(203),
                "like",
                SocialEventTypes.LIKE_CREATED,
                Instant.parse("2026-07-06T08:03:00Z"),
                45L,
                jsonCodec.valueToTree(likePayload(EntityTypes.COMMENT, uuid(203)))
        ));

        verifyNoInteractions(applicationService);
    }

    @Test
    void recognizedContentEventWithMissingPostIdShouldFailDelivery() {
        PostHotFeedProjectionApplicationService applicationService = mock(PostHotFeedProjectionApplicationService.class);
        PostHotFeedProjectionKafkaListener listener = listener(applicationService);

        assertThatThrownBy(() -> listener.onContentEvent(new ContentContractEvent(
                "evt-post-missing", null, null, ContentEventTypes.POST_UPDATED,
                Instant.EPOCH, 1L, jsonCodec.valueToTree(new PostPayload()))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(ContentEventTypes.POST_UPDATED)
                .hasMessageContaining("evt-post-missing");

        verifyNoInteractions(applicationService);
    }

    @Test
    void recognizedSocialEventWithInvalidSourceMetadataShouldFailDelivery() {
        PostHotFeedProjectionApplicationService applicationService = mock(PostHotFeedProjectionApplicationService.class);
        PostHotFeedProjectionKafkaListener listener = listener(applicationService);

        assertThatThrownBy(() -> listener.onSocialEvent(new SocialContractEvent(
                " ", null, null, SocialEventTypes.LIKE_CREATED, null, 0L,
                jsonCodec.valueToTree(likePayload(EntityTypes.POST, uuid(202))))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(SocialEventTypes.LIKE_CREATED);

        verifyNoInteractions(applicationService);
    }

    @Test
    void recognizedLikeWithoutProducerIdentityShouldFailDelivery() {
        PostHotFeedProjectionApplicationService applicationService = mock(PostHotFeedProjectionApplicationService.class);
        PostHotFeedProjectionKafkaListener listener = listener(applicationService);
        LikePayload payload = likePayload(EntityTypes.POST, uuid(202));
        payload.setActorUserId(null);

        assertThatThrownBy(() -> listener.onSocialEvent(new SocialContractEvent(
                "evt-like-missing-actor", uuid(202), "like", SocialEventTypes.LIKE_CREATED,
                Instant.EPOCH, 1L, jsonCodec.valueToTree(payload))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(SocialEventTypes.LIKE_CREATED)
                .hasMessageContaining("evt-like-missing-actor");

        verifyNoInteractions(applicationService);
    }

    @Test
    void recognizedPostLikeWithoutPostIdShouldFailDelivery() {
        PostHotFeedProjectionApplicationService applicationService = mock(PostHotFeedProjectionApplicationService.class);
        PostHotFeedProjectionKafkaListener listener = listener(applicationService);
        LikePayload payload = likePayload(EntityTypes.POST, uuid(202));
        payload.setPostId(null);

        assertThatThrownBy(() -> listener.onSocialEvent(new SocialContractEvent(
                "evt-like-missing-post-id", uuid(202), "like", SocialEventTypes.LIKE_CREATED,
                Instant.EPOCH, 1L, jsonCodec.valueToTree(payload))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("evt-like-missing-post-id");

        verifyNoInteractions(applicationService);
    }

    private PostHotFeedProjectionKafkaListener listener(
            PostHotFeedProjectionApplicationService applicationService
    ) {
        return new PostHotFeedProjectionKafkaListener(
                contentContractEventCodec, socialContractEventCodec, applicationService);
    }

    private static PostPayload postPayload(java.util.UUID postId, java.util.UUID boardId) {
        PostPayload payload = new PostPayload();
        payload.setPostId(postId);
        payload.setCategoryId(boardId);
        return payload;
    }

    private static LikePayload likePayload(int entityType, java.util.UUID postId) {
        LikePayload payload = new LikePayload();
        payload.setActorUserId(uuid(1));
        payload.setEntityType(entityType);
        payload.setEntityId(postId);
        payload.setPostId(postId);
        payload.setRelationKey("like:" + uuid(1) + ":" + entityType + ":" + postId);
        return payload;
    }
}
