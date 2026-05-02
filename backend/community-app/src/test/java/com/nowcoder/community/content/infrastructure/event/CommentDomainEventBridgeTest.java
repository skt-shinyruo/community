package com.nowcoder.community.content.infrastructure.event;

import com.nowcoder.community.content.application.ContentEventPublisher;
import com.nowcoder.community.content.contracts.event.CommentPayload;
import com.nowcoder.community.content.domain.event.CommentCreatedDomainEvent;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class CommentDomainEventBridgeTest {

    @Test
    void onCommentCreatedShouldCopyAllFieldsToCommentPayload() {
        ContentEventPublisher eventPublisher = mock(ContentEventPublisher.class);
        CommentDomainEventBridge bridge = new CommentDomainEventBridge(eventPublisher);
        Instant createTime = Instant.parse("2026-04-29T09:30:00Z");

        bridge.onCommentCreated(new CommentCreatedDomainEvent(
                uuid(1),
                uuid(2),
                uuid(3),
                4,
                uuid(5),
                uuid(6),
                "reply content",
                createTime
        ));

        ArgumentCaptor<CommentPayload> captor = ArgumentCaptor.forClass(CommentPayload.class);
        verify(eventPublisher).publishCommentCreated(captor.capture());
        CommentPayload payload = captor.getValue();
        assertThat(payload.getCommentId()).isEqualTo(uuid(1));
        assertThat(payload.getPostId()).isEqualTo(uuid(2));
        assertThat(payload.getUserId()).isEqualTo(uuid(3));
        assertThat(payload.getEntityType()).isEqualTo(4);
        assertThat(payload.getEntityId()).isEqualTo(uuid(5));
        assertThat(payload.getTargetUserId()).isEqualTo(uuid(6));
        assertThat(payload.getContent()).isEqualTo("reply content");
        assertThat(payload.getCreateTime()).isEqualTo(createTime);
    }
}
