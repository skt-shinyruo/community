package com.nowcoder.community.im.realtime.fanout;

import com.nowcoder.community.im.common.command.RoomFanoutCommand;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class RoomFanoutTargetConsumer {

    private final RoomFanoutTargetService targetService;
    private final RoomFanoutMetrics metrics;

    public RoomFanoutTargetConsumer(
            RoomFanoutTargetService targetService,
            RoomFanoutMetrics metrics
    ) {
        this.targetService = targetService;
        this.metrics = metrics;
    }

    @KafkaListener(
            topicPartitions = @org.springframework.kafka.annotation.TopicPartition(
                    topic = "${im.room-fanout.routed-command-topic:im.command.room-fanout-routed}",
                    partitions = "${im.room-fanout.worker-inbox-slot}"
            ),
            groupId = "${im.room-fanout.target-group-id:im-realtime-room-fanout-target}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onCommand(RoomFanoutCommand command) {
        RoomFanoutTargetResult result = targetService.apply(command);
        if (result == RoomFanoutTargetResult.ACCEPTED) {
            metrics.targetAccepted();
            return;
        }
        if (result == RoomFanoutTargetResult.DUPLICATE) {
            metrics.targetDuplicate();
            return;
        }
        metrics.targetRejected();
        throw new IllegalStateException("room fanout target command rejected: " + result);
    }
}
