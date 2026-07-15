package com.nowcoder.community.social.infrastructure.event;

import com.fasterxml.jackson.databind.JsonNode;
import com.nowcoder.community.common.json.JacksonJsonCodec;
import com.nowcoder.community.common.json.JsonCodec;
import com.nowcoder.community.common.json.JsonMappers;
import com.nowcoder.community.content.contracts.event.CommentPayload;
import com.nowcoder.community.content.contracts.event.ContentContractEvent;
import com.nowcoder.community.content.contracts.event.ContentContractEventCodec;
import com.nowcoder.community.content.contracts.event.ContentEventTypes;
import com.nowcoder.community.content.contracts.event.PostPayload;
import com.nowcoder.community.content.infrastructure.event.JacksonContentContractEventCodec;
import com.nowcoder.community.social.application.LikeApplicationService;
import com.nowcoder.community.social.application.command.CleanupDeletedContentLikesCommand;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.Arrays;
import java.util.Map;

import static com.nowcoder.community.common.constants.EntityTypes.COMMENT;
import static com.nowcoder.community.common.constants.EntityTypes.POST;
import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class SocialContentDeletionKafkaListenerTest {

    private static final Instant DELETED_AT = Instant.parse("2026-07-15T08:30:00Z");

    private final JsonCodec jsonCodec = new JacksonJsonCodec(JsonMappers.standard());
    private final ContentContractEventCodec contractEventCodec = new JacksonContentContractEventCodec(jsonCodec);

    @Test
    void postDeletedShouldMapTypedPayloadToSocialOwnedCleanupCommand() {
        LikeApplicationService applicationService = mock(LikeApplicationService.class);
        SocialContentDeletionKafkaListener listener = listener(applicationService);
        PostPayload payload = new PostPayload();
        payload.setPostId(uuid(100));

        listener.onContentEvent(contentEvent(
                "content:PostDeleted:" + uuid(100),
                ContentEventTypes.POST_DELETED,
                41L,
                payload
        ));

        assertThat(capturedCommand(applicationService)).isEqualTo(new CleanupDeletedContentLikesCommand(
                POST,
                uuid(100),
                "content:PostDeleted:" + uuid(100),
                41L,
                DELETED_AT
        ));
    }

    @Test
    void commentDeletedShouldMapMapLikePayloadToSocialOwnedCleanupCommand() {
        LikeApplicationService applicationService = mock(LikeApplicationService.class);
        SocialContentDeletionKafkaListener listener = listener(applicationService);

        listener.onContentEvent(contentEvent(
                "content:CommentDeleted:" + uuid(200),
                ContentEventTypes.COMMENT_DELETED,
                42L,
                Map.of("commentId", uuid(200).toString())
        ));

        assertThat(capturedCommand(applicationService)).isEqualTo(new CleanupDeletedContentLikesCommand(
                COMMENT,
                uuid(200),
                "content:CommentDeleted:" + uuid(200),
                42L,
                DELETED_AT
        ));
    }

    @Test
    void postDeletedShouldDecodeJsonNodePayloadBeforeApplicationBoundary() {
        LikeApplicationService applicationService = mock(LikeApplicationService.class);
        SocialContentDeletionKafkaListener listener = listener(applicationService);
        JsonNode payload = jsonCodec.valueToTree(Map.of("postId", uuid(101).toString()));

        listener.onContentEvent(contentEvent(
                "content:PostDeleted:" + uuid(101),
                ContentEventTypes.POST_DELETED,
                43L,
                payload
        ));

        assertThat(capturedCommand(applicationService).entityId()).isEqualTo(uuid(101));
    }

    @Test
    void unknownAndNullEventsShouldBeIgnored() {
        LikeApplicationService applicationService = mock(LikeApplicationService.class);
        SocialContentDeletionKafkaListener listener = listener(applicationService);

        listener.onContentEvent(null);
        listener.onContentEvent(new ContentContractEvent(
                null,
                null,
                null,
                ContentEventTypes.POST_UPDATED,
                null,
                0L,
                jsonCodec.valueToTree(Map.of("postId", uuid(100).toString()))
        ));
        listener.onContentEvent(new ContentContractEvent(
                null,
                null,
                null,
                "FutureContentEvent",
                null,
                0L,
                null
        ));

        verifyNoInteractions(applicationService);
    }

    @Test
    void recognizedDeletionWithMissingIdentityOrSourceMetadataShouldFailDelivery() {
        LikeApplicationService applicationService = mock(LikeApplicationService.class);
        SocialContentDeletionKafkaListener listener = listener(applicationService);
        CommentPayload missingCommentId = new CommentPayload();

        assertThatThrownBy(() -> listener.onContentEvent(contentEvent(
                "content:CommentDeleted:missing",
                ContentEventTypes.COMMENT_DELETED,
                44L,
                missingCommentId
        )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(ContentEventTypes.COMMENT_DELETED)
                .hasMessageContaining("content:CommentDeleted:missing");

        assertThatThrownBy(() -> listener.onContentEvent(new ContentContractEvent(
                " ",
                uuid(100),
                "post",
                ContentEventTypes.POST_DELETED,
                null,
                0L,
                jsonCodec.valueToTree(Map.of("postId", uuid(100).toString()))
        )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(ContentEventTypes.POST_DELETED);

        verifyNoInteractions(applicationService);
    }

    @Test
    void recognizedDeletionWithUndecodableIdentityShouldFailDelivery() {
        LikeApplicationService applicationService = mock(LikeApplicationService.class);
        SocialContentDeletionKafkaListener listener = listener(applicationService);

        assertThatThrownBy(() -> listener.onContentEvent(contentEvent(
                "content:PostDeleted:bad-id",
                ContentEventTypes.POST_DELETED,
                45L,
                Map.of("postId", "not-a-uuid")
        )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(ContentEventTypes.POST_DELETED)
                .hasMessageContaining("content:PostDeleted:bad-id");

        verifyNoInteractions(applicationService);
    }

    @Test
    void socialApplicationCommandShouldNotExposeForeignContentContracts() {
        assertThat(Arrays.stream(CleanupDeletedContentLikesCommand.class.getRecordComponents())
                .map(component -> component.getType().getPackageName()))
                .noneMatch(packageName -> packageName.startsWith("com.nowcoder.community.content"));
    }

    private SocialContentDeletionKafkaListener listener(LikeApplicationService applicationService) {
        return new SocialContentDeletionKafkaListener(contractEventCodec, applicationService);
    }

    private CleanupDeletedContentLikesCommand capturedCommand(LikeApplicationService applicationService) {
        ArgumentCaptor<CleanupDeletedContentLikesCommand> captor =
                ArgumentCaptor.forClass(CleanupDeletedContentLikesCommand.class);
        verify(applicationService).cleanupDeletedContentLikes(captor.capture());
        return captor.getValue();
    }

    private ContentContractEvent contentEvent(String eventId, String type, long version, Object payload) {
        return new ContentContractEvent(
                eventId,
                null,
                null,
                type,
                DELETED_AT,
                version,
                jsonCodec.valueToTree(payload)
        );
    }
}
