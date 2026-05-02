package com.nowcoder.community.content.application;

import com.nowcoder.community.content.contracts.event.PostPayload;
import org.junit.jupiter.api.Test;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PostContractEventApplicationServiceTest {

    @Test
    void publishPostPublishedShouldAssembleAndPublishPayload() {
        ContentPostPayloadAssembler assembler = mock(ContentPostPayloadAssembler.class);
        ContentEventPublisher eventPublisher = mock(ContentEventPublisher.class);
        PostContractEventApplicationService service =
                new PostContractEventApplicationService(assembler, eventPublisher);
        PostPayload payload = new PostPayload();
        payload.setPostId(uuid(11));
        when(assembler.assemble(uuid(11))).thenReturn(payload);

        service.publishPostPublished(uuid(11));

        var inOrder = inOrder(assembler, eventPublisher);
        inOrder.verify(assembler).assemble(uuid(11));
        inOrder.verify(eventPublisher).publishPostPublished(payload);
    }

    @Test
    void publishPostUpdatedShouldAssembleAndPublishPayload() {
        ContentPostPayloadAssembler assembler = mock(ContentPostPayloadAssembler.class);
        ContentEventPublisher eventPublisher = mock(ContentEventPublisher.class);
        PostContractEventApplicationService service =
                new PostContractEventApplicationService(assembler, eventPublisher);
        PostPayload payload = new PostPayload();
        payload.setPostId(uuid(12));
        when(assembler.assemble(uuid(12))).thenReturn(payload);

        service.publishPostUpdated(uuid(12));

        var inOrder = inOrder(assembler, eventPublisher);
        inOrder.verify(assembler).assemble(uuid(12));
        inOrder.verify(eventPublisher).publishPostUpdated(payload);
    }

    @Test
    void publishPostDeletedShouldAssembleAndPublishPayload() {
        ContentPostPayloadAssembler assembler = mock(ContentPostPayloadAssembler.class);
        ContentEventPublisher eventPublisher = mock(ContentEventPublisher.class);
        PostContractEventApplicationService service =
                new PostContractEventApplicationService(assembler, eventPublisher);
        PostPayload payload = new PostPayload();
        payload.setPostId(uuid(13));
        when(assembler.assemble(uuid(13))).thenReturn(payload);

        service.publishPostDeleted(uuid(13));

        var inOrder = inOrder(assembler, eventPublisher);
        inOrder.verify(assembler).assemble(uuid(13));
        inOrder.verify(eventPublisher).publishPostDeleted(payload);
    }
}
