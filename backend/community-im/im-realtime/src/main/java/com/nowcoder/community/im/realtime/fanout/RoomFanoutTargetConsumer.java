package com.nowcoder.community.im.realtime.fanout;

import com.nowcoder.community.im.common.command.RoomFanoutCommand;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnExpression("'${im.room-fanout.mode:legacy}' == 'routed' && '${im.room-fanout.transport:kafka}' == 'kafka'")
public class RoomFanoutTargetConsumer {

    private final RoomFanoutTargetService targetService;

    public RoomFanoutTargetConsumer(RoomFanoutTargetService targetService) {
        this.targetService = targetService;
    }

    @KafkaListener(
            topicPartitions = @org.springframework.kafka.annotation.TopicPartition(
                    topic = "${im.room-fanout.routed-command-topic:im.command.room-fanout-routed}",
                    partitions = "${im.room-fanout.worker-inbox-slot:0}"
            ),
            groupId = "${im.room-fanout.target-group-id:im-realtime-room-fanout-target}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onCommand(RoomFanoutCommand command) {
        RoomFanoutTargetResult result = targetService.apply(command);
        if (result == RoomFanoutTargetResult.ACCEPTED || result == RoomFanoutTargetResult.DUPLICATE) {
            return;
        }
        throw new IllegalStateException("room fanout target command rejected: " + result);
    }
}
