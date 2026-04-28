package com.nowcoder.community.search.infrastructure.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.common.outbox.OutboxEvent;
import com.nowcoder.community.content.api.model.PostScanView;
import com.nowcoder.community.content.api.query.PostScanQueryApi;
import com.nowcoder.community.search.application.SearchApplicationService;
import com.nowcoder.community.search.application.command.DeleteIndexedPostCommand;
import com.nowcoder.community.search.application.command.SyncPostProjectionCommand;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PostOutboxHandlerTest {

    @Test
    void handlerShouldSyncCurrentProjectionWhenPostExists() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

        PostScanQueryApi postScanQueryApi = mock(PostScanQueryApi.class);
        SearchApplicationService searchApplicationService = mock(SearchApplicationService.class);
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

        PostOutboxHandler handler = new PostOutboxHandler(objectMapper, postScanQueryApi, searchApplicationService);

        handler.handle(outboxEvent(objectMapper, postId, "src-s1", "PostUpdated"));

        ArgumentCaptor<SyncPostProjectionCommand> commandCaptor =
                ArgumentCaptor.forClass(SyncPostProjectionCommand.class);
        verify(searchApplicationService).syncPostProjection(commandCaptor.capture());
        verify(searchApplicationService, never()).deletePost(org.mockito.ArgumentMatchers.any());

        SyncPostProjectionCommand command = commandCaptor.getValue();
        assertThat(command.postId()).isEqualTo(postId);
        assertThat(command.userId()).isEqualTo(userId);
        assertThat(command.categoryId()).isEqualTo(categoryId);
        assertThat(command.tags()).containsExactly("java");
        assertThat(command.title()).isEqualTo("title");
        assertThat(command.content()).isEqualTo("content");
        assertThat(command.type()).isEqualTo(0);
        assertThat(command.status()).isEqualTo(0);
        assertThat(command.createTime()).isEqualTo(Instant.parse("2026-03-28T00:00:00Z"));
        assertThat(command.score()).isEqualTo(1.5);
    }

    @Test
    void handlerShouldPassDeletedProjectionToApplication() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

        PostScanQueryApi postScanQueryApi = mock(PostScanQueryApi.class);
        SearchApplicationService searchApplicationService = mock(SearchApplicationService.class);
        UUID postId = uuid(101);

        PostScanView.PostProjectionView doc = new PostScanView.PostProjectionView(
                postId,
                uuid(7),
                uuid(3),
                List.of("java"),
                "title",
                "content",
                0,
                2,
                Instant.parse("2026-03-28T00:00:00Z"),
                1.5
        );
        when(postScanQueryApi.getPostProjectionAllowDeleted(postId)).thenReturn(doc);

        PostOutboxHandler handler = new PostOutboxHandler(objectMapper, postScanQueryApi, searchApplicationService);

        handler.handle(outboxEvent(objectMapper, postId, "src-s2", "PostDeleted"));

        ArgumentCaptor<SyncPostProjectionCommand> commandCaptor =
                ArgumentCaptor.forClass(SyncPostProjectionCommand.class);
        verify(searchApplicationService).syncPostProjection(commandCaptor.capture());
        verify(searchApplicationService, never()).deletePost(org.mockito.ArgumentMatchers.any());
        assertThat(commandCaptor.getValue().status()).isEqualTo(2);
    }

    @Test
    void handlerShouldDeleteWhenProjectionNoLongerExists() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

        PostScanQueryApi postScanQueryApi = mock(PostScanQueryApi.class);
        SearchApplicationService searchApplicationService = mock(SearchApplicationService.class);
        UUID postId = uuid(101);
        when(postScanQueryApi.getPostProjectionAllowDeleted(postId)).thenReturn(null);

        PostOutboxHandler handler = new PostOutboxHandler(objectMapper, postScanQueryApi, searchApplicationService);

        handler.handle(outboxEvent(objectMapper, postId, "src-s3", "PostDeleted"));

        verify(searchApplicationService).deletePost(new DeleteIndexedPostCommand(postId));
        verify(searchApplicationService, never()).syncPostProjection(org.mockito.ArgumentMatchers.any());
    }

    private static OutboxEvent outboxEvent(
            ObjectMapper objectMapper,
            UUID postId,
            String sourceEventId,
            String sourceEventType
    ) throws Exception {
        String payloadJson = objectMapper.writeValueAsString(Map.of(
                "postId", postId,
                "sourceEventId", sourceEventId,
                "sourceEventType", sourceEventType
        ));
        return new OutboxEvent(
                UUID.fromString("01965429-b34a-7000-8000-000000000021"),
                sourceEventId + ":search_post",
                PostOutboxHandler.TOPIC,
                postId.toString(),
                payloadJson,
                "PENDING",
                0,
                null,
                null
        );
    }
}
