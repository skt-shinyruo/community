package com.nowcoder.community.content.application;

import com.nowcoder.community.content.contracts.event.PostPayload;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PostIntegrationEventPublisherTest {

    @Test
    void integrationEventsMustJoinTheExistingPostWriteTransaction() {
        for (String methodName : new String[]{"postPublished", "postUpdated", "postDeleted"}) {
            Transactional transactional = requiredMethod(methodName).getAnnotation(Transactional.class);

            assertThat(transactional).isNotNull();
            assertThat(transactional.propagation()).isEqualTo(Propagation.MANDATORY);
        }
    }

    @Test
    void publishPostPublishedShouldAssembleAndPublishPayload() {
        ContentPostPayloadAssembler assembler = mock(ContentPostPayloadAssembler.class);
        ContentEventPublisher eventPublisher = mock(ContentEventPublisher.class);
        PostCacheAfterCommit cacheAfterCommit = mock(PostCacheAfterCommit.class);
        PostIntegrationEventPublisher publisher =
                new PostIntegrationEventPublisher(assembler, eventPublisher, cacheAfterCommit);
        PostPayload payload = new PostPayload();
        payload.setPostId(uuid(11));
        when(assembler.assemble(uuid(11))).thenReturn(payload);

        publisher.postPublished(uuid(11));

        var inOrder = inOrder(assembler, eventPublisher);
        inOrder.verify(assembler).assemble(uuid(11));
        inOrder.verify(eventPublisher).publishPostPublished(payload);
        verifyNoInteractions(cacheAfterCommit);
    }

    @Test
    void publishPostUpdatedShouldAssembleAndPublishPayload() {
        ContentPostPayloadAssembler assembler = mock(ContentPostPayloadAssembler.class);
        ContentEventPublisher eventPublisher = mock(ContentEventPublisher.class);
        PostCacheAfterCommit cacheAfterCommit = mock(PostCacheAfterCommit.class);
        PostIntegrationEventPublisher publisher =
                new PostIntegrationEventPublisher(assembler, eventPublisher, cacheAfterCommit);
        PostPayload payload = new PostPayload();
        payload.setPostId(uuid(12));
        payload.setAggregateVersion(7L);
        when(assembler.assemble(uuid(12))).thenReturn(payload);

        publisher.postUpdated(uuid(12));

        var inOrder = inOrder(assembler, eventPublisher);
        inOrder.verify(assembler).assemble(uuid(12));
        inOrder.verify(eventPublisher).publishPostUpdated(payload);
        verify(cacheAfterCommit).evict(uuid(12), 7L);
    }

    @Test
    void publishPostDeletedShouldAssembleAndPublishPayload() {
        ContentPostPayloadAssembler assembler = mock(ContentPostPayloadAssembler.class);
        ContentEventPublisher eventPublisher = mock(ContentEventPublisher.class);
        PostCacheAfterCommit cacheAfterCommit = mock(PostCacheAfterCommit.class);
        PostIntegrationEventPublisher publisher =
                new PostIntegrationEventPublisher(assembler, eventPublisher, cacheAfterCommit);
        PostPayload payload = new PostPayload();
        payload.setPostId(uuid(13));
        payload.setCategoryId(uuid(14));
        payload.setAggregateVersion(8L);
        when(assembler.assemble(uuid(13))).thenReturn(payload);

        publisher.postDeleted(uuid(13));

        var inOrder = inOrder(assembler, eventPublisher);
        inOrder.verify(assembler).assemble(uuid(13));
        inOrder.verify(eventPublisher).publishPostDeleted(payload);
        verify(cacheAfterCommit).terminalEvict(uuid(13), uuid(14), 8L);
    }

    private Method requiredMethod(String name) {
        try {
            return PostIntegrationEventPublisher.class.getDeclaredMethod(name, java.util.UUID.class);
        } catch (NoSuchMethodException exception) {
            throw new AssertionError(exception);
        }
    }
}
