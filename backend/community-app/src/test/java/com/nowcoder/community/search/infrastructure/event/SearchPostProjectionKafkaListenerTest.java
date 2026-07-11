package com.nowcoder.community.search.infrastructure.event;

import com.nowcoder.community.common.json.JacksonJsonCodec;
import com.nowcoder.community.common.json.JsonCodec;
import com.nowcoder.community.common.json.JsonMappers;
import com.nowcoder.community.content.contracts.event.CommentPayload;
import com.nowcoder.community.content.contracts.event.ContentContractEvent;
import com.nowcoder.community.content.contracts.event.ContentEventTypes;
import com.nowcoder.community.content.contracts.event.PostPayload;
import com.nowcoder.community.search.application.SearchPostProjectionApplicationService;
import com.nowcoder.community.search.application.command.ProjectPostCommand;
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

class SearchPostProjectionKafkaListenerTest {

    private final JsonCodec jsonCodec = new JacksonJsonCodec(JsonMappers.standard());

    @Test
    void postUpdatedShouldDelegateToSearchProjectionApplication() {
        SearchPostProjectionApplicationService applicationService = mock(SearchPostProjectionApplicationService.class);
        SearchPostProjectionKafkaListener listener = new SearchPostProjectionKafkaListener(jsonCodec, applicationService);
        PostPayload payload = new PostPayload();
        payload.setPostId(uuid(100));

        listener.onContentEvent(contentEvent("evt-post-updated", ContentEventTypes.POST_UPDATED, 42L, payload));

        ArgumentCaptor<ProjectPostCommand> captor = ArgumentCaptor.forClass(ProjectPostCommand.class);
        verify(applicationService).projectPost(captor.capture());
        assertThat(captor.getValue()).isEqualTo(new ProjectPostCommand(
                uuid(100),
                "evt-post-updated",
                42L
        ));
    }

    @Test
    void mapLikePostPayloadShouldConvertBeforeDelegation() {
        SearchPostProjectionApplicationService applicationService = mock(SearchPostProjectionApplicationService.class);
        SearchPostProjectionKafkaListener listener = new SearchPostProjectionKafkaListener(jsonCodec, applicationService);

        listener.onContentEvent(contentEvent(
                "evt-post-map",
                ContentEventTypes.POST_UPDATED,
                43L,
                Map.of("postId", uuid(101).toString())
        ));

        ArgumentCaptor<ProjectPostCommand> captor = ArgumentCaptor.forClass(ProjectPostCommand.class);
        verify(applicationService).projectPost(captor.capture());
        assertThat(captor.getValue()).isEqualTo(new ProjectPostCommand(
                uuid(101),
                "evt-post-map",
                43L
        ));
    }

    @Test
    void commentCreatedShouldBeIgnored() {
        SearchPostProjectionApplicationService applicationService = mock(SearchPostProjectionApplicationService.class);
        SearchPostProjectionKafkaListener listener = new SearchPostProjectionKafkaListener(jsonCodec, applicationService);

        listener.onContentEvent(new ContentContractEvent("evt-comment-created", null, null, ContentEventTypes.COMMENT_CREATED, java.time.Instant.EPOCH, 1L, new CommentPayload()));

        verifyNoInteractions(applicationService);
    }

    @Test
    void recognizedEventWithMissingPostIdShouldFailDelivery() {
        SearchPostProjectionApplicationService applicationService = mock(SearchPostProjectionApplicationService.class);
        SearchPostProjectionKafkaListener listener = new SearchPostProjectionKafkaListener(jsonCodec, applicationService);

        assertThatThrownBy(() -> listener.onContentEvent(new ContentContractEvent(
                "evt-post-missing", null, null, ContentEventTypes.POST_UPDATED,
                Instant.EPOCH, 1L, new PostPayload())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(ContentEventTypes.POST_UPDATED)
                .hasMessageContaining("evt-post-missing");

        verifyNoInteractions(applicationService);
    }

    @Test
    void recognizedEventWithInvalidSourceMetadataShouldFailDelivery() {
        SearchPostProjectionApplicationService applicationService = mock(SearchPostProjectionApplicationService.class);
        SearchPostProjectionKafkaListener listener = new SearchPostProjectionKafkaListener(jsonCodec, applicationService);
        PostPayload payload = new PostPayload();
        payload.setPostId(uuid(100));

        assertThatThrownBy(() -> listener.onContentEvent(new ContentContractEvent(
                " ", null, null, ContentEventTypes.POST_DELETED, null, 0L, payload)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(ContentEventTypes.POST_DELETED);

        verifyNoInteractions(applicationService);
    }

    private static ContentContractEvent contentEvent(String eventId, String type, long version, Object payload) {
        return new ContentContractEvent(
                eventId,
                null,
                null,
                type,
                Instant.parse("2026-07-06T00:00:00Z"),
                version,
                payload
        );
    }
}
