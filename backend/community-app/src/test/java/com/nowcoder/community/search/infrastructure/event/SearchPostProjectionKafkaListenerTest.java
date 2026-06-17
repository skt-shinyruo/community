package com.nowcoder.community.search.infrastructure.event;

import com.nowcoder.community.common.json.JacksonJsonCodec;
import com.nowcoder.community.common.json.JsonCodec;
import com.nowcoder.community.common.json.JsonMappers;
import com.nowcoder.community.content.contracts.event.CommentPayload;
import com.nowcoder.community.content.contracts.event.ContentContractEvent;
import com.nowcoder.community.content.contracts.event.ContentEventTypes;
import com.nowcoder.community.content.contracts.event.PostPayload;
import com.nowcoder.community.search.application.SearchPostProjectionApplicationService;
import com.nowcoder.community.search.application.command.ProjectPostOutboxCommand;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
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

        listener.onContentEvent(new ContentContractEvent("evt-post-updated", ContentEventTypes.POST_UPDATED, payload));

        ArgumentCaptor<ProjectPostOutboxCommand> captor = ArgumentCaptor.forClass(ProjectPostOutboxCommand.class);
        verify(applicationService).projectPostFromOutbox(captor.capture());
        assertThat(captor.getValue()).isEqualTo(new ProjectPostOutboxCommand(
                uuid(100),
                "evt-post-updated",
                ContentEventTypes.POST_UPDATED
        ));
    }

    @Test
    void mapLikePostPayloadShouldConvertBeforeDelegation() {
        SearchPostProjectionApplicationService applicationService = mock(SearchPostProjectionApplicationService.class);
        SearchPostProjectionKafkaListener listener = new SearchPostProjectionKafkaListener(jsonCodec, applicationService);

        listener.onContentEvent(new ContentContractEvent(
                "evt-post-map",
                ContentEventTypes.POST_UPDATED,
                Map.of("postId", uuid(101).toString())
        ));

        ArgumentCaptor<ProjectPostOutboxCommand> captor = ArgumentCaptor.forClass(ProjectPostOutboxCommand.class);
        verify(applicationService).projectPostFromOutbox(captor.capture());
        assertThat(captor.getValue()).isEqualTo(new ProjectPostOutboxCommand(
                uuid(101),
                "evt-post-map",
                ContentEventTypes.POST_UPDATED
        ));
    }

    @Test
    void commentCreatedShouldBeIgnored() {
        SearchPostProjectionApplicationService applicationService = mock(SearchPostProjectionApplicationService.class);
        SearchPostProjectionKafkaListener listener = new SearchPostProjectionKafkaListener(jsonCodec, applicationService);

        listener.onContentEvent(new ContentContractEvent(
                "evt-comment-created",
                ContentEventTypes.COMMENT_CREATED,
                new CommentPayload()
        ));

        verifyNoInteractions(applicationService);
    }

    @Test
    void missingPostProjectionFieldsShouldBeIgnored() {
        SearchPostProjectionApplicationService applicationService = mock(SearchPostProjectionApplicationService.class);
        SearchPostProjectionKafkaListener listener = new SearchPostProjectionKafkaListener(jsonCodec, applicationService);

        listener.onContentEvent(null);
        listener.onContentEvent(new ContentContractEvent("evt-post-missing", ContentEventTypes.POST_UPDATED, new PostPayload()));
        listener.onContentEvent(new ContentContractEvent("evt-post-map-missing", ContentEventTypes.POST_DELETED, Map.of("userId", uuid(7).toString())));

        verifyNoInteractions(applicationService);
    }
}
