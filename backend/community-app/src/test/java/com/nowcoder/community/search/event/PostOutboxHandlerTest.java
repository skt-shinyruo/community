package com.nowcoder.community.search.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.content.api.model.PostScanView;
import com.nowcoder.community.content.api.query.PostScanQueryApi;
import com.nowcoder.community.content.contracts.event.PostPayload;
import com.nowcoder.community.common.outbox.OutboxEvent;
import com.nowcoder.community.search.repo.PostSearchRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.eq;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PostOutboxHandlerTest {

    @Test
    void handlerShouldUpsertWhenPostIsActive() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

        PostScanQueryApi postScanQueryApi = mock(PostScanQueryApi.class);
        PostSearchRepository repository = mock(PostSearchRepository.class);

        PostScanView.PostProjectionView doc = new PostScanView.PostProjectionView(
                101,
                7,
                3,
                List.of("java"),
                "title",
                "content",
                0,
                0,
                Instant.parse("2026-03-28T00:00:00Z"),
                1.5
        );
        when(postScanQueryApi.getPostProjectionAllowDeleted(101)).thenReturn(doc);

        PostOutboxHandler handler = new PostOutboxHandler(objectMapper, postScanQueryApi, repository);

        String payloadJson = objectMapper.writeValueAsString(Map.of("postId", 101, "sourceEventId", "src-s1", "sourceEventType", "PostUpdated"));
        OutboxEvent event = new OutboxEvent(1L, "src-s1:search_post", PostOutboxHandler.TOPIC, "101", payloadJson, "PENDING", 0, null, null);

        handler.handle(event);

        ArgumentCaptor<PostPayload> payloadCaptor = ArgumentCaptor.forClass(PostPayload.class);
        verify(repository).upsert(payloadCaptor.capture());
        verify(repository, never()).delete(eq(101));

        PostPayload payload = payloadCaptor.getValue();
        assertThat(payload.getPostId()).isEqualTo(101);
        assertThat(payload.getUserId()).isEqualTo(7);
        assertThat(payload.getCategoryId()).isEqualTo(3);
        assertThat(payload.getTags()).containsExactly("java");
        assertThat(payload.getTitle()).isEqualTo("title");
        assertThat(payload.getContent()).isEqualTo("content");
        assertThat(payload.getType()).isEqualTo(0);
        assertThat(payload.getStatus()).isEqualTo(0);
        assertThat(payload.getCreateTime()).isEqualTo(Instant.parse("2026-03-28T00:00:00Z"));
        assertThat(payload.getScore()).isEqualTo(1.5);
    }

    @Test
    void handlerShouldDeleteWhenPostIsDeleted() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

        PostScanQueryApi postScanQueryApi = mock(PostScanQueryApi.class);
        PostSearchRepository repository = mock(PostSearchRepository.class);

        PostScanView.PostProjectionView doc = new PostScanView.PostProjectionView(
                101,
                7,
                3,
                List.of("java"),
                "title",
                "content",
                0,
                2,
                Instant.parse("2026-03-28T00:00:00Z"),
                1.5
        );
        when(postScanQueryApi.getPostProjectionAllowDeleted(101)).thenReturn(doc);

        PostOutboxHandler handler = new PostOutboxHandler(objectMapper, postScanQueryApi, repository);

        String payloadJson = objectMapper.writeValueAsString(Map.of("postId", 101, "sourceEventId", "src-s2", "sourceEventType", "PostDeleted"));
        OutboxEvent event = new OutboxEvent(1L, "src-s2:search_post", PostOutboxHandler.TOPIC, "101", payloadJson, "PENDING", 0, null, null);

        handler.handle(event);

        verify(repository).delete(eq(101));
        verify(repository, never()).upsert(any());
    }
}
