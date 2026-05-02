package com.nowcoder.community.content.infrastructure.event;

import com.nowcoder.community.content.application.PostContractEventApplicationService;
import com.nowcoder.community.content.domain.event.PostDeletedDomainEvent;
import com.nowcoder.community.content.domain.event.PostPublishedDomainEvent;
import com.nowcoder.community.content.domain.event.PostUpdatedDomainEvent;
import org.junit.jupiter.api.Test;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class PostDomainEventBridgeTest {

    @Test
    void onPostPublishedShouldDelegateToApplicationService() {
        PostContractEventApplicationService applicationService = mock(PostContractEventApplicationService.class);
        PostDomainEventBridge bridge = new PostDomainEventBridge(applicationService);

        bridge.onPostPublished(new PostPublishedDomainEvent(uuid(11)));

        verify(applicationService).publishPostPublished(uuid(11));
    }

    @Test
    void onPostUpdatedShouldDelegateToApplicationService() {
        PostContractEventApplicationService applicationService = mock(PostContractEventApplicationService.class);
        PostDomainEventBridge bridge = new PostDomainEventBridge(applicationService);

        bridge.onPostUpdated(new PostUpdatedDomainEvent(uuid(12)));

        verify(applicationService).publishPostUpdated(uuid(12));
    }

    @Test
    void onPostDeletedShouldDelegateToApplicationService() {
        PostContractEventApplicationService applicationService = mock(PostContractEventApplicationService.class);
        PostDomainEventBridge bridge = new PostDomainEventBridge(applicationService);

        bridge.onPostDeleted(new PostDeletedDomainEvent(uuid(13)));

        verify(applicationService).publishPostDeleted(uuid(13));
    }
}
