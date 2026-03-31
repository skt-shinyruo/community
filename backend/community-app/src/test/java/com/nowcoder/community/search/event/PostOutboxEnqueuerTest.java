package com.nowcoder.community.search.event;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.content.contracts.event.ContentContractEvent;
import com.nowcoder.community.content.contracts.event.ContentEventTypes;
import com.nowcoder.community.content.contracts.event.PostPayload;
import com.nowcoder.community.common.outbox.JdbcOutboxEventStore;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PostOutboxEnqueuerTest {

    @Test
    void postUpdatedShouldEnqueueSearchProjection() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        JdbcOutboxEventStore store = mock(JdbcOutboxEventStore.class);
        when(store.enqueue(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(true);

        PostOutboxEnqueuer enqueuer = new PostOutboxEnqueuer(objectMapper, store);

        PostPayload payload = new PostPayload();
        payload.setPostId(101);

        enqueuer.onContentEvent(new ContentContractEvent("evt-s1", ContentEventTypes.POST_UPDATED, payload));

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(store).enqueue(org.mockito.ArgumentMatchers.eq("evt-s1:search_post"), org.mockito.ArgumentMatchers.eq(PostOutboxHandler.TOPIC), org.mockito.ArgumentMatchers.eq("101"), payloadCaptor.capture());

        JsonNode json = objectMapper.readTree(payloadCaptor.getValue());
        assertThat(json.path("postId").asInt()).isEqualTo(101);
        assertThat(json.path("sourceEventId").asText()).isEqualTo("evt-s1");
        assertThat(json.path("sourceEventType").asText()).isEqualTo(ContentEventTypes.POST_UPDATED);
    }
}
