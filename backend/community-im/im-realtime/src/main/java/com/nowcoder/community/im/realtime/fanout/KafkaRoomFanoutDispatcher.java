package com.nowcoder.community.im.realtime.fanout;

import com.nowcoder.community.common.kafka.trace.TraceKafkaHeaders;
import com.nowcoder.community.im.common.command.RoomFanoutCommand;
import com.nowcoder.community.im.realtime.session.ImSessionProperties;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Primary;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Primary
@Component
@ConditionalOnExpression("'${im.room-fanout.transport:kafka}' == 'kafka'")
public class KafkaRoomFanoutDispatcher implements RoomFanoutDispatcher {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final RealtimeWorkerDirectory workerDirectory;
    private final RoomFanoutProperties properties;
    private final String localWorkerId;

    public KafkaRoomFanoutDispatcher(
            KafkaTemplate<String, Object> kafkaTemplate,
            RealtimeWorkerDirectory workerDirectory,
            RoomFanoutProperties properties,
            ImSessionProperties sessionProperties
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.workerDirectory = workerDirectory;
        this.properties = properties;
        this.localWorkerId = normalize(sessionProperties == null ? null : sessionProperties.getWorkerId());
    }

    @Override
    public void dispatch(RoomFanoutCommand command) {
        if (command == null || !StringUtils.hasText(command.targetWorkerId())) {
            return;
        }
        int partition = targetPartition(command.targetWorkerId());
        ProducerRecord<String, Object> record = new ProducerRecord<>(
                properties.normalizedRoutedCommandTopic(),
                partition,
                command.targetWorkerId().trim(),
                command
        );
        TraceKafkaHeaders.inject(record.headers());
        try {
            kafkaTemplate.send(record)
                    .get(properties.normalizedTargetTimeout().toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while publishing room fanout command", ex);
        } catch (ExecutionException | TimeoutException ex) {
            throw new IllegalStateException("Failed to publish room fanout command", ex);
        }
    }

    private int targetPartition(String targetWorkerId) {
        String normalizedTarget = normalize(targetWorkerId);
        if (normalizedTarget.equals(localWorkerId)) {
            return properties.normalizedWorkerInboxSlot();
        }
        RealtimeWorkerEndpoint endpoint = workerDirectory.find(normalizedTarget)
                .orElseThrow(() -> new IllegalStateException("Realtime worker not found: " + normalizedTarget));
        if (endpoint.roomFanoutInboxSlot() == null) {
            throw new IllegalStateException("Realtime worker room fanout inbox slot not found: " + normalizedTarget);
        }
        return endpoint.roomFanoutInboxSlot();
    }

    private static String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : "";
    }
}
