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
        ContentReadModelsAfterCommit readModelsAfterCommit = mock(ContentReadModelsAfterCommit.class);
        PostIntegrationEventPublisher publisher =
                new PostIntegrationEventPublisher(assembler, eventPublisher, readModelsAfterCommit);
        PostPayload payload = new PostPayload(
                uuid(11), null, null, null, null, null, 0, 0, null, null, null, 0L, 0L);
        when(assembler.assemble(uuid(11))).thenReturn(payload);

        publisher.postPublished(uuid(11));

        var inOrder = inOrder(assembler, eventPublisher);
        inOrder.verify(assembler).assemble(uuid(11));
        inOrder.verify(eventPublisher).publishPostPublished(payload);
        verifyNoInteractions(readModelsAfterCommit);
    }

    @Test
    void publishPostUpdatedShouldAssembleAndPublishPayload() {
        ContentPostPayloadAssembler assembler = mock(ContentPostPayloadAssembler.class);
        ContentEventPublisher eventPublisher = mock(ContentEventPublisher.class);
        ContentReadModelsAfterCommit readModelsAfterCommit = mock(ContentReadModelsAfterCommit.class);
        PostIntegrationEventPublisher publisher =
                new PostIntegrationEventPublisher(assembler, eventPublisher, readModelsAfterCommit);
        PostPayload payload = new PostPayload(
                uuid(12), null, null, null, null, null, 0, 0, null, null, null, 0L, 7L);
        when(assembler.assemble(uuid(12))).thenReturn(payload);

        publisher.postUpdated(uuid(12));

        var inOrder = inOrder(assembler, eventPublisher);
        inOrder.verify(assembler).assemble(uuid(12));
        inOrder.verify(eventPublisher).publishPostUpdated(payload);
        verify(readModelsAfterCommit).postUpdated(uuid(12), 7L);
    }

    @Test
    void publishPostDeletedShouldAssembleAndPublishPayload() {
        ContentPostPayloadAssembler assembler = mock(ContentPostPayloadAssembler.class);
        ContentEventPublisher eventPublisher = mock(ContentEventPublisher.class);
        ContentReadModelsAfterCommit readModelsAfterCommit = mock(ContentReadModelsAfterCommit.class);
        PostIntegrationEventPublisher publisher =
                new PostIntegrationEventPublisher(assembler, eventPublisher, readModelsAfterCommit);
        PostPayload payload = new PostPayload(
                uuid(13), null, uuid(14), null, null, null, 0, 0, null, null, null, 0L, 8L);
        when(assembler.assemble(uuid(13))).thenReturn(payload);

        publisher.postDeleted(uuid(13));

        var inOrder = inOrder(assembler, eventPublisher);
        inOrder.verify(assembler).assemble(uuid(13));
        inOrder.verify(eventPublisher).publishPostDeleted(payload);
        verify(readModelsAfterCommit).postDeleted(uuid(13), uuid(14), 8L);
    }

    private Method requiredMethod(String name) {
        try {
            return PostIntegrationEventPublisher.class.getDeclaredMethod(name, java.util.UUID.class);
        } catch (NoSuchMethodException exception) {
            throw new AssertionError(exception);
        }
    }
}
