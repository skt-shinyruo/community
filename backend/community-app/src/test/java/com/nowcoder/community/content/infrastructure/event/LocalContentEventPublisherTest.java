package com.nowcoder.community.content.infrastructure.event;

import com.nowcoder.community.content.contracts.event.ContentContractEvent;
import com.nowcoder.community.content.contracts.event.ContentEventTypes;
import com.nowcoder.community.content.contracts.event.PostPayload;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class LocalContentEventPublisherTest {

    @Test
    void publishPostPublishedShouldStampBackboneMetadata() {
        ApplicationEventPublisher springPublisher = mock(ApplicationEventPublisher.class);
        LocalContentEventPublisher publisher = new LocalContentEventPublisher(springPublisher);
        PostPayload payload = new PostPayload();
        payload.setPostId(uuid(101));
        payload.setCreateTime(Instant.parse("2026-07-06T08:00:00Z"));

        publisher.publishPostPublished(payload);

        ContentContractEvent event = captureEvent(springPublisher);
        assertThat(event.eventId()).isNotBlank();
        assertThat(event.aggregateId()).isEqualTo(uuid(101));
        assertThat(event.aggregateType()).isEqualTo("post");
        assertThat(event.type()).isEqualTo(ContentEventTypes.POST_PUBLISHED);
        assertThat(event.occurredAt()).isEqualTo(Instant.parse("2026-07-06T08:00:00Z"));
        assertThat(event.version()).isEqualTo(Instant.parse("2026-07-06T08:00:00Z").toEpochMilli());
        assertThat(event.payload()).isSameAs(payload);
    }

    @Test
    void publishPostPublishedShouldRejectMissingSourceTimestamp() {
        ApplicationEventPublisher springPublisher = mock(ApplicationEventPublisher.class);
        LocalContentEventPublisher publisher = new LocalContentEventPublisher(springPublisher);
        PostPayload payload = new PostPayload();
        payload.setPostId(uuid(102));

        assertThatThrownBy(() -> publisher.publishPostPublished(payload))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("content event source occurredAt missing");

        verifyNoInteractions(springPublisher);
    }

    private ContentContractEvent captureEvent(ApplicationEventPublisher springPublisher) {
        ArgumentCaptor<ContentContractEvent> captor = ArgumentCaptor.forClass(ContentContractEvent.class);
        verify(springPublisher).publishEvent(captor.capture());
        return captor.getValue();
    }
}
