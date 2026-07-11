package com.nowcoder.community.im.realtime.fanout;

import com.nowcoder.community.im.common.event.RoomMessagePersistedEvent;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class RoomPersistedOwnerConsumer {

    private final RoomFanoutOwnerService ownerService;
    private final RoomFanoutMetrics metrics;

    public RoomPersistedOwnerConsumer(
            RoomFanoutOwnerService ownerService,
            RoomFanoutMetrics metrics
    ) {
        this.ownerService = ownerService;
        this.metrics = metrics;
    }

    @KafkaListener(
            topics = "${im.kafka.topics.event-room-persisted:im.event.room-persisted}",
            groupId = "${im.room-fanout.owner-group-id:im-realtime-room-fanout-owner}",
            containerFactory = "kafkaListenerContainerFactory",
            concurrency = "${im.room-fanout.owner-concurrency:${im.kafka.event.concurrency:3}}"
    )
    public void onRoomPersisted(RoomMessagePersistedEvent event) {
        if (event == null) {
            return;
        }
        metrics.ownerEventConsumed();
        ownerService.routeAndDispatch(event);
    }
}
