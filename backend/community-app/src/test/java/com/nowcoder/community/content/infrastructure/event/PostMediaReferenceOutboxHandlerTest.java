package com.nowcoder.community.content.infrastructure.event;

import com.nowcoder.community.common.json.JacksonJsonCodec;
import com.nowcoder.community.common.json.JsonCodec;
import com.nowcoder.community.common.json.JsonMappers;
import com.nowcoder.community.common.outbox.OutboxEvent;
import com.nowcoder.community.common.outbox.OutboxEventStatus;
import com.nowcoder.community.common.outbox.OutboxHandler;
import com.nowcoder.community.content.application.PostMediaReferenceApplicationService;
import com.nowcoder.community.content.application.PostMediaStoragePort;
import com.nowcoder.community.content.application.command.PostMediaReferenceCommand;
import com.nowcoder.community.content.domain.model.PostMediaReferenceOperation;
import com.nowcoder.community.content.domain.repository.PostMediaAssetRepository;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class PostMediaReferenceOutboxHandlerTest {

    private static final String TOPIC = "command.content.post-media-reference";
    private static final UUID ASSET_ID = UUID.fromString("00000000-0000-7000-8000-000000004001");
    private static final UUID ACTOR_USER_ID = UUID.fromString("00000000-0000-7000-8000-000000004002");

    @Test
    void handlerShouldDecodeTheCommandAndEnterOnlyTheSameDomainApplicationService() {
        JsonCodec jsonCodec = new JacksonJsonCodec(JsonMappers.standard());
        PostMediaReferenceApplicationService applicationService = mock(PostMediaReferenceApplicationService.class);
        PostMediaReferenceOutboxHandler handler = new PostMediaReferenceOutboxHandler(
                jsonCodec,
                applicationService,
                TOPIC
        );
        PostMediaReferenceCommand command = new PostMediaReferenceCommand(
                ASSET_ID,
                PostMediaReferenceOperation.RELEASE,
                9L,
                ACTOR_USER_ID
        );
        OutboxEvent event = new OutboxEvent(
                UUID.fromString("00000000-0000-7000-8000-000000004003"),
                "content-media-reference:" + ASSET_ID + ":9:RELEASE",
                TOPIC,
                ASSET_ID.toString(),
                jsonCodec.toJson(command),
                OutboxEventStatus.PROCESSING,
                1,
                null,
                null,
                null,
                null
        );

        handler.handle(event);

        assertThat(handler.topic()).isEqualTo(TOPIC);
        verify(applicationService).process(command);
    }

    @Test
    void handlerDependencySurfaceShouldNotBypassTheApplicationBoundary() {
        assertThat(PostMediaReferenceOutboxHandler.class.getInterfaces())
                .containsExactly(OutboxHandler.class);
        assertThat(Arrays.stream(PostMediaReferenceOutboxHandler.class.getDeclaredFields())
                .map(Field::getType))
                .contains(PostMediaReferenceApplicationService.class, JsonCodec.class)
                .doesNotContain(PostMediaAssetRepository.class, PostMediaStoragePort.class);
    }
}
