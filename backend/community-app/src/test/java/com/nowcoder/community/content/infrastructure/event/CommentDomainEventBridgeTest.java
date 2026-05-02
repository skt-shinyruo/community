package com.nowcoder.community.content.infrastructure.event;

import com.nowcoder.community.content.application.CommentContractEventApplicationService;
import com.nowcoder.community.content.domain.event.CommentCreatedDomainEvent;
import com.nowcoder.community.content.domain.event.CommentDeletedDomainEvent;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class CommentDomainEventBridgeTest {

    @Test
    void onCommentCreatedShouldDelegateToApplicationService() {
        CommentContractEventApplicationService applicationService = mock(CommentContractEventApplicationService.class);
        CommentDomainEventBridge bridge = new CommentDomainEventBridge(applicationService);
        Instant createTime = Instant.parse("2026-04-29T09:30:00Z");

        CommentCreatedDomainEvent event = new CommentCreatedDomainEvent(
                uuid(1),
                uuid(2),
                uuid(3),
                4,
                uuid(5),
                uuid(6),
                "reply content",
                createTime
        );

        bridge.onCommentCreated(event);

        verify(applicationService).publishCommentCreated(event);
    }

    @Test
    void onCommentDeletedShouldDelegateToApplicationService() {
        CommentContractEventApplicationService applicationService = mock(CommentContractEventApplicationService.class);
        CommentDomainEventBridge bridge = new CommentDomainEventBridge(applicationService);
        Instant deletedTime = Instant.parse("2026-04-29T09:30:00Z");

        CommentDeletedDomainEvent event = new CommentDeletedDomainEvent(
                uuid(1),
                uuid(2),
                uuid(3),
                4,
                uuid(5),
                deletedTime
        );

        bridge.onCommentDeleted(event);

        verify(applicationService).publishCommentDeleted(event);
    }
}
