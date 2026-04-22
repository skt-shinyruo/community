package com.nowcoder.community.growth.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.content.contracts.event.ContentContractEvent;
import com.nowcoder.community.content.contracts.event.ContentEventTypes;
import com.nowcoder.community.content.contracts.event.PostPayload;
import com.nowcoder.community.growth.service.GrowthBusinessTimeService;
import com.nowcoder.community.common.outbox.JdbcOutboxEventStore;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class TaskProgressOutboxEnqueuerTest {

    @Test
    void postPublishedShouldUseBusinessZoneWhenEnqueuingTaskProgress() {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        JdbcOutboxEventStore store = mock(JdbcOutboxEventStore.class);
        GrowthBusinessTimeService businessTimeService = new GrowthBusinessTimeService(
                Clock.fixed(Instant.parse("2026-03-22T00:00:00Z"), ZoneId.of("Asia/Shanghai")),
                ZoneId.of("Asia/Shanghai")
        );
        TaskProgressOutboxEnqueuer enqueuer = new TaskProgressOutboxEnqueuer(objectMapper, store, businessTimeService);
        UUID userId = uuid(7);

        PostPayload payload = new PostPayload();
        payload.setUserId(userId);
        payload.setCreateTime(Instant.parse("2026-03-21T16:30:00Z"));

        enqueuer.onContentEvent(new ContentContractEvent("post-evt-1", ContentEventTypes.POST_PUBLISHED, payload));

        verify(store).enqueue(
                eq("post-evt-1:task-progress"),
                eq(TaskProgressOutboxHandler.TOPIC),
                eq(userId.toString()),
                eq("{\"userId\":\"" + userId + "\",\"triggerEventType\":\"PostPublished\",\"sourceEventId\":\"post-evt-1\",\"bizDate\":[2026,3,22]}")
        );
    }
}
