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
        PostIntegrationEventPublisher publisher =
                new PostIntegrationEventPublisher(assembler, eventPublisher);
        PostPayload payload = new PostPayload();
        payload.setPostId(uuid(11));
        when(assembler.assemble(uuid(11))).thenReturn(payload);

        publisher.postPublished(uuid(11));

        var inOrder = inOrder(assembler, eventPublisher);
        inOrder.verify(assembler).assemble(uuid(11));
        inOrder.verify(eventPublisher).publishPostPublished(payload);
    }

    @Test
    void publishPostUpdatedShouldAssembleAndPublishPayload() {
        ContentPostPayloadAssembler assembler = mock(ContentPostPayloadAssembler.class);
        ContentEventPublisher eventPublisher = mock(ContentEventPublisher.class);
        PostIntegrationEventPublisher publisher =
                new PostIntegrationEventPublisher(assembler, eventPublisher);
        PostPayload payload = new PostPayload();
        payload.setPostId(uuid(12));
        when(assembler.assemble(uuid(12))).thenReturn(payload);

        publisher.postUpdated(uuid(12));

        var inOrder = inOrder(assembler, eventPublisher);
        inOrder.verify(assembler).assemble(uuid(12));
        inOrder.verify(eventPublisher).publishPostUpdated(payload);
    }

    @Test
    void publishPostDeletedShouldAssembleAndPublishPayload() {
        ContentPostPayloadAssembler assembler = mock(ContentPostPayloadAssembler.class);
        ContentEventPublisher eventPublisher = mock(ContentEventPublisher.class);
        PostIntegrationEventPublisher publisher =
                new PostIntegrationEventPublisher(assembler, eventPublisher);
        PostPayload payload = new PostPayload();
        payload.setPostId(uuid(13));
        when(assembler.assemble(uuid(13))).thenReturn(payload);

        publisher.postDeleted(uuid(13));

        var inOrder = inOrder(assembler, eventPublisher);
        inOrder.verify(assembler).assemble(uuid(13));
        inOrder.verify(eventPublisher).publishPostDeleted(payload);
    }

    private Method requiredMethod(String name) {
        try {
            return PostIntegrationEventPublisher.class.getDeclaredMethod(name, java.util.UUID.class);
        } catch (NoSuchMethodException exception) {
            throw new AssertionError(exception);
        }
    }
}
