package com.nowcoder.community.search.infrastructure.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.common.outbox.OutboxEvent;
import com.nowcoder.community.search.application.SearchPostProjectionApplicationService;
import com.nowcoder.community.search.application.command.ProjectPostOutboxCommand;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;
import java.util.UUID;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class PostOutboxHandlerTest {

    @Test
    void handlerShouldDeserializePayloadAndDelegateToApplication() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        SearchPostProjectionApplicationService projectionApplicationService =
                mock(SearchPostProjectionApplicationService.class);
        UUID postId = uuid(101);

        PostOutboxHandler handler = new PostOutboxHandler(objectMapper, projectionApplicationService);

        handler.handle(outboxEvent(objectMapper, postId, "src-s1", "PostUpdated"));

        ArgumentCaptor<ProjectPostOutboxCommand> captor = ArgumentCaptor.forClass(ProjectPostOutboxCommand.class);
        verify(projectionApplicationService).projectPostFromOutbox(captor.capture());
        assertThat(captor.getValue().postId()).isEqualTo(postId);
        assertThat(captor.getValue().sourceEventId()).isEqualTo("src-s1");
        assertThat(captor.getValue().sourceEventType()).isEqualTo("PostUpdated");
    }

    @Test
    void handlerShouldIgnoreBlankPayload() {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        SearchPostProjectionApplicationService projectionApplicationService =
                mock(SearchPostProjectionApplicationService.class);
        PostOutboxHandler handler = new PostOutboxHandler(objectMapper, projectionApplicationService);

        handler.handle(new OutboxEvent(
                UUID.fromString("01965429-b34a-7000-8000-000000000021"),
                "aggregate",
                PostOutboxHandler.TOPIC,
                "key",
                " ",
                "PENDING",
                0,
                null,
                null,
                null,
                null
        ));

        verifyNoInteractions(projectionApplicationService);
    }

    @Test
    void handlerShouldFailInvalidPayload() {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        SearchPostProjectionApplicationService projectionApplicationService =
                mock(SearchPostProjectionApplicationService.class);
        PostOutboxHandler handler = new PostOutboxHandler(objectMapper, projectionApplicationService);

        Throwable thrown = catchThrowable(() -> handler.handle(new OutboxEvent(
                UUID.fromString("01965429-b34a-7000-8000-000000000021"),
                "aggregate",
                PostOutboxHandler.TOPIC,
                "key",
                "{",
                "PENDING",
                0,
                null,
                null,
                null,
                null
        )));

        assertThat(thrown)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("search outbox payload 反序列化失败");
        verifyNoInteractions(projectionApplicationService);
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
                null,
                null,
                null
        );
    }
}
