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
import java.util.UUID;

import static com.nowcoder.community.support.TestUuids.uuid;
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
        UUID postId = uuid(101);
        UUID userId = uuid(7);
        UUID categoryId = uuid(3);

        PostScanView.PostProjectionView doc = new PostScanView.PostProjectionView(
                postId,
                userId,
                categoryId,
                List.of("java"),
                "title",
                "content",
                0,
                0,
                Instant.parse("2026-03-28T00:00:00Z"),
                1.5
        );
        when(postScanQueryApi.getPostProjectionAllowDeleted(postId)).thenReturn(doc);

        PostOutboxHandler handler = new PostOutboxHandler(objectMapper, postScanQueryApi, repository);

        String payloadJson = objectMapper.writeValueAsString(Map.of("postId", postId, "sourceEventId", "src-s1", "sourceEventType", "PostUpdated"));
        OutboxEvent event = new OutboxEvent(
                UUID.fromString("01965429-b34a-7000-8000-000000000021"),
                "src-s1:search_post",
                PostOutboxHandler.TOPIC,
                postId.toString(),
                payloadJson,
                "PENDING",
                0,
                null,
                null
        );

        handler.handle(event);

        ArgumentCaptor<PostPayload> payloadCaptor = ArgumentCaptor.forClass(PostPayload.class);
        verify(repository).upsert(payloadCaptor.capture());
        verify(repository, never()).delete(eq(postId));

        PostPayload payload = payloadCaptor.getValue();
        assertThat(payload.getPostId()).isEqualTo(postId);
        assertThat(payload.getUserId()).isEqualTo(userId);
        assertThat(payload.getCategoryId()).isEqualTo(categoryId);
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
        UUID postId = uuid(101);
        UUID userId = uuid(7);
        UUID categoryId = uuid(3);

        PostScanView.PostProjectionView doc = new PostScanView.PostProjectionView(
                postId,
                userId,
                categoryId,
                List.of("java"),
                "title",
                "content",
                0,
                2,
                Instant.parse("2026-03-28T00:00:00Z"),
                1.5
        );
        when(postScanQueryApi.getPostProjectionAllowDeleted(postId)).thenReturn(doc);

        PostOutboxHandler handler = new PostOutboxHandler(objectMapper, postScanQueryApi, repository);

        String payloadJson = objectMapper.writeValueAsString(Map.of("postId", postId, "sourceEventId", "src-s2", "sourceEventType", "PostDeleted"));
        OutboxEvent event = new OutboxEvent(
                UUID.fromString("01965429-b34a-7000-8000-000000000022"),
                "src-s2:search_post",
                PostOutboxHandler.TOPIC,
                postId.toString(),
                payloadJson,
                "PENDING",
                0,
                null,
                null
        );

        handler.handle(event);

        verify(repository).delete(eq(postId));
        verify(repository, never()).upsert(any());
    }
}
