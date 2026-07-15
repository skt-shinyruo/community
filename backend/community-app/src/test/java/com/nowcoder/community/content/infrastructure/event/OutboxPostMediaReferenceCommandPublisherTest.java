package com.nowcoder.community.content.infrastructure.event;

import com.fasterxml.jackson.databind.JsonNode;
import com.nowcoder.community.common.json.JacksonJsonCodec;
import com.nowcoder.community.common.json.JsonMappers;
import com.nowcoder.community.common.outbox.JdbcOutboxEventStore;
import com.nowcoder.community.content.application.PostMediaReferenceCommandPublisher;
import com.nowcoder.community.content.application.command.PostMediaReferenceCommand;
import com.nowcoder.community.content.domain.model.PostMediaReferenceOperation;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class OutboxPostMediaReferenceCommandPublisherTest {

    private static final String TOPIC = "command.content.post-media-reference";
    private static final UUID ASSET_ID = UUID.fromString("00000000-0000-7000-8000-000000002001");
    private static final UUID ACTOR_USER_ID = UUID.fromString("00000000-0000-7000-8000-000000002002");

    @Test
    void commandShouldContainOnlyReplaySafeIdentityFields() {
        assertThat(PostMediaReferenceCommand.class.isRecord()).isTrue();
        assertThat(Arrays.stream(PostMediaReferenceCommand.class.getRecordComponents())
                .map(RecordComponent::getName))
                .containsExactly("assetId", "operation", "operationVersion", "actorUserId");
        assertThat(Arrays.stream(PostMediaReferenceCommand.class.getRecordComponents())
                .map(RecordComponent::getType))
                .containsExactly(UUID.class, PostMediaReferenceOperation.class, long.class, UUID.class);
    }

    @Test
    void publisherShouldImplementTheApplicationOwnedCommandPort() {
        assertThat(OutboxPostMediaReferenceCommandPublisher.class.getInterfaces())
                .containsExactly(PostMediaReferenceCommandPublisher.class);
    }

    @Test
    void publisherShouldUseStableOperationVersionEventIdAndMinimalPayload() throws Exception {
        JdbcOutboxEventStore store = mock(JdbcOutboxEventStore.class);
        OutboxPostMediaReferenceCommandPublisher publisher = new OutboxPostMediaReferenceCommandPublisher(
                new JacksonJsonCodec(JsonMappers.standard()),
                store,
                TOPIC
        );
        PostMediaReferenceCommand command = new PostMediaReferenceCommand(
                ASSET_ID,
                PostMediaReferenceOperation.BIND,
                7L,
                ACTOR_USER_ID
        );

        publisher.publish(command);

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(store).enqueue(
                eq("content-media-reference:" + ASSET_ID + ":7:BIND"),
                eq(TOPIC),
                eq(ASSET_ID.toString()),
                payloadCaptor.capture()
        );
        JsonNode payload = JsonMappers.standard().readTree(payloadCaptor.getValue());
        List<String> fieldNames = new ArrayList<>();
        payload.fieldNames().forEachRemaining(fieldNames::add);
        assertThat(fieldNames)
                .containsExactlyInAnyOrder("assetId", "operation", "operationVersion", "actorUserId");
        assertThat(payload.path("assetId").asText()).isEqualTo(ASSET_ID.toString());
        assertThat(payload.path("operation").asText()).isEqualTo("BIND");
        assertThat(payload.path("operationVersion").asLong()).isEqualTo(7L);
        assertThat(payload.path("actorUserId").asText()).isEqualTo(ACTOR_USER_ID.toString());
    }
}
