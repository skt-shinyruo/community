package com.nowcoder.community.content.infrastructure.event;

import com.nowcoder.community.common.constants.EntityTypes;
import com.nowcoder.community.common.json.JacksonJsonCodec;
import com.nowcoder.community.content.application.PostHotFeedProjectionApplicationService;
import com.nowcoder.community.content.application.PostProjectionVersionLane;
import com.nowcoder.community.content.application.PostHotFeedProjectionApplicationService.ProjectPostHotFeedCommand;
import com.nowcoder.community.content.contracts.event.CommentPayload;
import com.nowcoder.community.content.contracts.event.ContentContractEvent;
import com.nowcoder.community.content.contracts.event.ContentContractEventCodec;
import com.nowcoder.community.content.contracts.event.ContentEventTypes;
import com.nowcoder.community.content.contracts.event.PostPayload;
import com.nowcoder.community.content.contracts.event.PostScorePayload;
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

    private final JacksonJsonCodec jsonCodec = new JacksonJsonCodec(JacksonJsonCodec.standardMapper());
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
                jsonCodec.valueToTree(postPayload(uuid(200), uuid(10), 42L))
        ));

        ArgumentCaptor<ProjectPostHotFeedCommand> captor = ArgumentCaptor.forClass(ProjectPostHotFeedCommand.class);
        verify(applicationService).project(captor.capture());
        assertThat(captor.getValue().sourceEventId()).isEqualTo("evt-post-published");
        assertThat(captor.getValue().sourceVersion()).isEqualTo(42L);
        assertThat(captor.getValue().sourceVersionLane()).isEqualTo(PostProjectionVersionLane.POST);
        assertThat(captor.getValue().postId()).isEqualTo(uuid(200));
        assertThat(captor.getValue().boardId()).isEqualTo(uuid(10));
        assertThat(captor.getValue().terminalDeletion()).isFalse();
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
        assertThat(captor.getValue().sourceVersionLane()).isEqualTo(PostProjectionVersionLane.LEGACY_POST);
        assertThat(captor.getValue().terminalDeletion()).isFalse();
    }

    @Test
    void postDeletedShouldMarkProjectionAsTerminalDeletion() {
        PostHotFeedProjectionApplicationService applicationService = mock(PostHotFeedProjectionApplicationService.class);
        PostHotFeedProjectionKafkaListener listener = listener(applicationService);

        listener.onContentEvent(new ContentContractEvent(
                "evt-post-deleted",
                uuid(201),
                "post",
                ContentEventTypes.POST_DELETED,
                Instant.parse("2026-07-06T08:01:30Z"),
                5L,
                jsonCodec.valueToTree(postPayload(uuid(201), uuid(11), 5L))
        ));

        ArgumentCaptor<ProjectPostHotFeedCommand> captor = ArgumentCaptor.forClass(ProjectPostHotFeedCommand.class);
        verify(applicationService).project(captor.capture());
        assertThat(captor.getValue().postId()).isEqualTo(uuid(201));
        assertThat(captor.getValue().sourceVersion()).isEqualTo(5L);
        assertThat(captor.getValue().sourceVersionLane()).isEqualTo(PostProjectionVersionLane.POST);
        assertThat(captor.getValue().terminalDeletion()).isTrue();
    }

    @Test
    void postScoreUpdatedShouldNotFeedBackIntoHotScoreRecomputation() {
        PostHotFeedProjectionApplicationService applicationService = mock(PostHotFeedProjectionApplicationService.class);
        PostHotFeedProjectionKafkaListener listener = listener(applicationService);

        listener.onContentEvent(new ContentContractEvent(
                "evt-score-3",
                uuid(201),
                "post",
                ContentEventTypes.POST_SCORE_UPDATED,
                Instant.parse("2026-07-06T08:01:20Z"),
                3L,
                jsonCodec.valueToTree(new PostScorePayload(uuid(201), 5L, 3L, 19.5))
        ));

        verifyNoInteractions(applicationService);
    }

    @Test
    void postDeletedWithMismatchedAggregateIdShouldFailDelivery() {
        PostHotFeedProjectionApplicationService applicationService = mock(PostHotFeedProjectionApplicationService.class);
        PostHotFeedProjectionKafkaListener listener = listener(applicationService);

        assertThatThrownBy(() -> listener.onContentEvent(new ContentContractEvent(
                "evt-post-deleted-wrong-id",
                uuid(202),
                "post",
                ContentEventTypes.POST_DELETED,
                Instant.parse("2026-07-06T08:01:31Z"),
                5L,
                jsonCodec.valueToTree(postPayload(uuid(201), uuid(11), 5L))
        )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(ContentEventTypes.POST_DELETED)
                .hasMessageContaining("evt-post-deleted-wrong-id");

        verifyNoInteractions(applicationService);
    }

    @Test
    void postDeletedWithMismatchedAggregateTypeShouldFailDelivery() {
        PostHotFeedProjectionApplicationService applicationService = mock(PostHotFeedProjectionApplicationService.class);
        PostHotFeedProjectionKafkaListener listener = listener(applicationService);

        assertThatThrownBy(() -> listener.onContentEvent(new ContentContractEvent(
                "evt-post-deleted-wrong-type",
                uuid(201),
                "comment",
                ContentEventTypes.POST_DELETED,
                Instant.parse("2026-07-06T08:01:32Z"),
                5L,
                jsonCodec.valueToTree(postPayload(uuid(201), uuid(11), 5L))
        )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(ContentEventTypes.POST_DELETED)
                .hasMessageContaining("evt-post-deleted-wrong-type");

        verifyNoInteractions(applicationService);
    }

    @Test
    void commentDeletedShouldNotMarkProjectionAsTerminalDeletion() {
        PostHotFeedProjectionApplicationService applicationService = mock(PostHotFeedProjectionApplicationService.class);
        PostHotFeedProjectionKafkaListener listener = listener(applicationService);

        listener.onContentEvent(new ContentContractEvent(
                "evt-comment-deleted",
                uuid(211),
                "comment",
                ContentEventTypes.COMMENT_DELETED,
                Instant.parse("2026-07-06T08:01:45Z"),
                6L,
                jsonCodec.valueToTree(commentPayload(uuid(201)))
        ));

        ArgumentCaptor<ProjectPostHotFeedCommand> captor = ArgumentCaptor.forClass(ProjectPostHotFeedCommand.class);
        verify(applicationService).project(captor.capture());
        assertThat(captor.getValue().postId()).isEqualTo(uuid(201));
        assertThat(captor.getValue().sourceVersion()).isEqualTo(6L);
        assertThat(captor.getValue().terminalDeletion()).isFalse();
        assertThat(captor.getValue().sourceVersionLane()).isEqualTo(PostProjectionVersionLane.COMMENT);
    }

    @Test
    void versionedCommentShouldShareThePostAggregateVersionLane() {
        PostHotFeedProjectionApplicationService applicationService = mock(PostHotFeedProjectionApplicationService.class);
        PostHotFeedProjectionKafkaListener listener = listener(applicationService);
        CommentPayload payload = new CommentPayload(
                uuid(211), uuid(201), uuid(1), 0, null, null, null, null, 51L);

        listener.onContentEvent(new ContentContractEvent(
                "evt-comment-created-versioned",
                uuid(211),
                "comment",
                ContentEventTypes.COMMENT_CREATED,
                Instant.parse("2026-07-06T08:01:46Z"),
                6L,
                jsonCodec.valueToTree(payload)
        ));

        ArgumentCaptor<ProjectPostHotFeedCommand> captor = ArgumentCaptor.forClass(ProjectPostHotFeedCommand.class);
        verify(applicationService).project(captor.capture());
        assertThat(captor.getValue().postId()).isEqualTo(uuid(201));
        assertThat(captor.getValue().sourceVersion()).isEqualTo(51L);
        assertThat(captor.getValue().sourceVersionLane()).isEqualTo(PostProjectionVersionLane.POST);
        assertThat(captor.getValue().terminalDeletion()).isFalse();
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
        assertThat(captor.getValue().sourceVersionLane()).isEqualTo(PostProjectionVersionLane.SOCIAL);
        assertThat(captor.getValue().terminalDeletion()).isFalse();
    }

    @Test
    void postEventWithMismatchedAggregateVersionShouldFailDelivery() {
        PostHotFeedProjectionApplicationService applicationService = mock(PostHotFeedProjectionApplicationService.class);
        PostHotFeedProjectionKafkaListener listener = listener(applicationService);

        assertThatThrownBy(() -> listener.onContentEvent(new ContentContractEvent(
                "opaque-post-event",
                uuid(204),
                "Post",
                ContentEventTypes.POST_UPDATED,
                Instant.EPOCH,
                9L,
                jsonCodec.valueToTree(postPayload(uuid(204), uuid(12), 8L))
        )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(ContentEventTypes.POST_UPDATED)
                .hasMessageContaining("opaque-post-event");

        verifyNoInteractions(applicationService);
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
                Instant.EPOCH, 1L, jsonCodec.valueToTree(new PostPayload(
                        null, null, null, null, null, null, 0, 0,
                        null, null, null, 0L, 0L)))))
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
        LikePayload payload = new LikePayload(
                null, EntityTypes.POST, uuid(202), null, uuid(202),
                "like:" + uuid(1) + ":" + EntityTypes.POST + ":" + uuid(202), null, null);

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
        LikePayload payload = new LikePayload(
                uuid(1), EntityTypes.POST, uuid(202), null, null,
                "like:" + uuid(1) + ":" + EntityTypes.POST + ":" + uuid(202), null, null);

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

    private static PostPayload postPayload(java.util.UUID postId, java.util.UUID boardId, long aggregateVersion) {
        return new PostPayload(
                postId, null, boardId, null, null, null, 0, 0,
                null, null, null, 0L, aggregateVersion);
    }

    private static LikePayload likePayload(int entityType, java.util.UUID postId) {
        return new LikePayload(
                uuid(1), entityType, postId, null, postId,
                "like:" + uuid(1) + ":" + entityType + ":" + postId, null, null);
    }

    private static CommentPayload commentPayload(java.util.UUID postId) {
        return new CommentPayload(uuid(211), postId, uuid(1), 0, null, null, null, null, 0L);
    }
}
