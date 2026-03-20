package com.nowcoder.community.search.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.content.service.PostScanService;
import com.nowcoder.community.content.event.payload.PostPayload;
import com.nowcoder.community.infra.outbox.OutboxEvent;
import com.nowcoder.community.search.repo.PostSearchRepository;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PostOutboxHandlerTest {

    @Test
    void handlerShouldUpsertWhenPostIsActive() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

        PostScanService postScanApplicationService = mock(PostScanService.class);
        PostSearchRepository repository = mock(PostSearchRepository.class);

        PostPayload doc = new PostPayload();
        doc.setPostId(101);
        doc.setStatus(0);
        when(postScanApplicationService.getPostPayloadAllowDeleted(101)).thenReturn(doc);

        PostOutboxHandler handler = new PostOutboxHandler(objectMapper, postScanApplicationService, repository);

        String payloadJson = objectMapper.writeValueAsString(Map.of("postId", 101, "sourceEventId", "src-s1", "sourceEventType", "PostUpdated"));
        OutboxEvent event = new OutboxEvent(1L, "src-s1:search_post", PostOutboxHandler.TOPIC, "101", payloadJson, "PENDING", 0, null, null);

        handler.handle(event);

        verify(repository).upsert(any());
        verify(repository, never()).delete(eq(101));
    }

    @Test
    void handlerShouldDeleteWhenPostIsDeleted() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

        PostScanService postScanApplicationService = mock(PostScanService.class);
        PostSearchRepository repository = mock(PostSearchRepository.class);

        PostPayload doc = new PostPayload();
        doc.setPostId(101);
        doc.setStatus(2); // deleted
        when(postScanApplicationService.getPostPayloadAllowDeleted(101)).thenReturn(doc);

        PostOutboxHandler handler = new PostOutboxHandler(objectMapper, postScanApplicationService, repository);

        String payloadJson = objectMapper.writeValueAsString(Map.of("postId", 101, "sourceEventId", "src-s2", "sourceEventType", "PostDeleted"));
        OutboxEvent event = new OutboxEvent(1L, "src-s2:search_post", PostOutboxHandler.TOPIC, "101", payloadJson, "PENDING", 0, null, null);

        handler.handle(event);

        verify(repository).delete(eq(101));
        verify(repository, never()).upsert(any());
    }
}
