package com.nowcoder.community.im.realtime.fanout;

import com.nowcoder.community.im.common.event.RoomMessagePersistedEvent;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnExpression("'${im.room-fanout.mode:legacy}' == 'shadow' || '${im.room-fanout.mode:legacy}' == 'routed'")
public class RoomPersistedOwnerConsumer {

    private final RoomFanoutOwnerCoalescer ownerCoalescer;
    private final RoomFanoutMetrics metrics;
    private final RoomFanoutProperties properties;

    public RoomPersistedOwnerConsumer(
            RoomFanoutOwnerCoalescer ownerCoalescer,
            RoomFanoutMetrics metrics,
            RoomFanoutProperties properties
    ) {
        this.ownerCoalescer = ownerCoalescer;
        this.metrics = metrics;
        this.properties = properties;
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
        if (properties.isShadowMode()) {
            ownerCoalescer.markRoomUpdated(event);
            return;
        }
        if (properties.isRoutedMode()) {
            ownerCoalescer.routeAndDispatchNow(event);
        }
    }
}
