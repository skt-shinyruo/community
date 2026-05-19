package com.nowcoder.community.im.realtime.fanout;

import com.nowcoder.community.im.common.event.RoomMessagePersistedEvent;
import com.nowcoder.community.im.realtime.push.RoomFanoutCoalescer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnExpression("'${im.room-fanout.mode:legacy}' == 'legacy' || '${im.room-fanout.mode:legacy}' == 'shadow'")
public class RoomPersistedLegacyConsumer {

    private final RoomFanoutCoalescer roomFanoutCoalescer;
    private final RoomFanoutMetrics metrics;

    public RoomPersistedLegacyConsumer(
            RoomFanoutCoalescer roomFanoutCoalescer,
            RoomFanoutMetrics metrics
    ) {
        this.roomFanoutCoalescer = roomFanoutCoalescer;
        this.metrics = metrics;
    }

    @KafkaListener(
            topics = "${im.kafka.topics.event-room-persisted:im.event.room-persisted}",
            containerFactory = "kafkaListenerContainerFactory",
            concurrency = "${im.kafka.event.concurrency:3}"
    )
    public void onRoomPersisted(RoomMessagePersistedEvent event) {
        if (event == null) {
            return;
        }
        metrics.legacyEventConsumed();
        roomFanoutCoalescer.markRoomUpdated(event.roomId(), event.seq());
    }
}
