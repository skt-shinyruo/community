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

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class PostHotFeedProjectionLocalListenerTest {

    private final JsonCodec jsonCodec = new JacksonJsonCodec(JsonMappers.standard());

    @Test
    void localContentEventShouldProjectHotFeedCommand() {
        PostHotFeedProjectionApplicationService applicationService = mock(PostHotFeedProjectionApplicationService.class);
        PostHotFeedProjectionLocalListener listener = new PostHotFeedProjectionLocalListener(jsonCodec, applicationService);

        listener.onContentEvent(new ContentContractEvent(
                "evt-post-published-local",
                uuid(301),
                "post",
                ContentEventTypes.POST_PUBLISHED,
                Instant.parse("2026-07-06T08:10:00Z"),
                51L,
                postPayload(uuid(301), uuid(21))
        ));

        ArgumentCaptor<ProjectPostHotFeedCommand> captor = ArgumentCaptor.forClass(ProjectPostHotFeedCommand.class);
        verify(applicationService).project(captor.capture());
        assertThat(captor.getValue().sourceEventId()).isEqualTo("evt-post-published-local");
        assertThat(captor.getValue().sourceVersion()).isEqualTo(51L);
        assertThat(captor.getValue().postId()).isEqualTo(uuid(301));
        assertThat(captor.getValue().boardId()).isEqualTo(uuid(21));
    }

    @Test
    void localNonPostSocialSignalsShouldBeIgnored() {
        PostHotFeedProjectionApplicationService applicationService = mock(PostHotFeedProjectionApplicationService.class);
        PostHotFeedProjectionLocalListener listener = new PostHotFeedProjectionLocalListener(jsonCodec, applicationService);

        listener.onSocialEvent(new SocialContractEvent(
                "evt-like-comment-local",
                uuid(302),
                "like",
                SocialEventTypes.LIKE_CREATED,
                Instant.parse("2026-07-06T08:11:00Z"),
                52L,
                likePayload(EntityTypes.COMMENT, uuid(302))
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
