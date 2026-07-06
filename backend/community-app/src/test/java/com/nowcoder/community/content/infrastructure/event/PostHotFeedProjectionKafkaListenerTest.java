package com.nowcoder.community.content.infrastructure.event;

import com.nowcoder.community.common.constants.EntityTypes;
import com.nowcoder.community.common.json.JacksonJsonCodec;
import com.nowcoder.community.common.json.JsonCodec;
import com.nowcoder.community.common.json.JsonMappers;
import com.nowcoder.community.content.application.PostHotFeedProjectionApplicationService;
import com.nowcoder.community.content.application.command.ProjectPostHotFeedCommand;
import com.nowcoder.community.content.contracts.event.ContentContractEvent;
import com.nowcoder.community.content.contracts.event.ContentEventTypes;
import com.nowcoder.community.content.contracts.event.PostPayload;
import com.nowcoder.community.social.contracts.event.LikePayload;
import com.nowcoder.community.social.contracts.event.SocialContractEvent;
import com.nowcoder.community.social.contracts.event.SocialEventTypes;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.Map;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class PostHotFeedProjectionKafkaListenerTest {

    private final JsonCodec jsonCodec = new JacksonJsonCodec(JsonMappers.standard());

    @Test
    void postPublishedShouldMapTypedContentPayloadToProjectionCommand() {
        PostHotFeedProjectionApplicationService applicationService = mock(PostHotFeedProjectionApplicationService.class);
        PostHotFeedProjectionKafkaListener listener = new PostHotFeedProjectionKafkaListener(jsonCodec, applicationService);

        listener.onContentEvent(new ContentContractEvent(
                "evt-post-published",
                uuid(200),
                "post",
                ContentEventTypes.POST_PUBLISHED,
                Instant.parse("2026-07-06T08:00:00Z"),
                42L,
                postPayload(uuid(200), uuid(10))
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
        PostHotFeedProjectionKafkaListener listener = new PostHotFeedProjectionKafkaListener(jsonCodec, applicationService);

        listener.onContentEvent(new ContentContractEvent(
                "evt-post-map",
                uuid(201),
                "post",
                ContentEventTypes.POST_UPDATED,
                Instant.parse("2026-07-06T08:01:00Z"),
                43L,
                Map.of("postId", uuid(201).toString(), "categoryId", uuid(11).toString())
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
        PostHotFeedProjectionKafkaListener listener = new PostHotFeedProjectionKafkaListener(jsonCodec, applicationService);

        listener.onSocialEvent(new SocialContractEvent(
                "evt-like-created",
                uuid(202),
                "like",
                SocialEventTypes.LIKE_CREATED,
                Instant.parse("2026-07-06T08:02:00Z"),
                44L,
                likePayload(EntityTypes.POST, uuid(202))
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
        PostHotFeedProjectionKafkaListener listener = new PostHotFeedProjectionKafkaListener(jsonCodec, applicationService);

        listener.onSocialEvent(new SocialContractEvent(
                "evt-like-comment",
                uuid(203),
                "like",
                SocialEventTypes.LIKE_CREATED,
                Instant.parse("2026-07-06T08:03:00Z"),
                45L,
                likePayload(EntityTypes.COMMENT, uuid(203))
        ));

        verifyNoInteractions(applicationService);
    }

    private static PostPayload postPayload(java.util.UUID postId, java.util.UUID boardId) {
        PostPayload payload = new PostPayload();
        payload.setPostId(postId);
        payload.setCategoryId(boardId);
        return payload;
    }

    private static LikePayload likePayload(int entityType, java.util.UUID postId) {
        LikePayload payload = new LikePayload();
        payload.setEntityType(entityType);
        payload.setEntityId(postId);
        payload.setPostId(postId);
        return payload;
    }
}
