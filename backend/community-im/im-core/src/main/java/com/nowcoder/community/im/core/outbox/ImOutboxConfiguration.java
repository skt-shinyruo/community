package com.nowcoder.community.im.core.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.common.outbox.OutboxHandler;
import com.nowcoder.community.im.common.event.PrivateMessagePersistedEvent;
import com.nowcoder.community.im.common.event.PrivateMessageRejectedEvent;
import com.nowcoder.community.im.common.event.RoomMemberChanged;
import com.nowcoder.community.im.common.event.RoomMessagePersistedEvent;
import com.nowcoder.community.im.common.event.RoomMessageRejectedEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;

@Configuration
@ConditionalOnClass(KafkaTemplate.class)
@ConditionalOnProperty(prefix = "events.outbox", name = "enabled", havingValue = "true")
public class ImOutboxConfiguration {

    @Bean
    public OutboxHandler imPrivatePersistedKafkaOutboxHandler(
            ObjectMapper objectMapper,
            KafkaTemplate<String, Object> kafkaTemplate,
            @Value("${im.kafka.topics.event-private-persisted:im.event.private-persisted}") String topic
    ) {
        return new ImKafkaOutboxHandler<>(
                topic,
                PrivateMessagePersistedEvent.class,
                objectMapper,
                kafkaTemplate
        );
    }

    @Bean
    public OutboxHandler imRoomPersistedKafkaOutboxHandler(
            ObjectMapper objectMapper,
            KafkaTemplate<String, Object> kafkaTemplate,
            @Value("${im.kafka.topics.event-room-persisted:im.event.room-persisted}") String topic
    ) {
        return new ImKafkaOutboxHandler<>(
                topic,
                RoomMessagePersistedEvent.class,
                objectMapper,
                kafkaTemplate
        );
    }

    @Bean
    public OutboxHandler imPrivateRejectedKafkaOutboxHandler(
            ObjectMapper objectMapper,
            KafkaTemplate<String, Object> kafkaTemplate,
            @Value("${im.kafka.topics.event-private-rejected:im.event.private-rejected}") String topic
    ) {
        return new ImKafkaOutboxHandler<>(
                topic,
                PrivateMessageRejectedEvent.class,
                objectMapper,
                kafkaTemplate
        );
    }

    @Bean
    public OutboxHandler imRoomRejectedKafkaOutboxHandler(
            ObjectMapper objectMapper,
            KafkaTemplate<String, Object> kafkaTemplate,
            @Value("${im.kafka.topics.event-room-rejected:im.event.room-rejected}") String topic
    ) {
        return new ImKafkaOutboxHandler<>(
                topic,
                RoomMessageRejectedEvent.class,
                objectMapper,
                kafkaTemplate
        );
    }

    @Bean
    public OutboxHandler imRoomMemberChangedKafkaOutboxHandler(
            ObjectMapper objectMapper,
            KafkaTemplate<String, Object> kafkaTemplate,
            @Value("${im.kafka.topics.event-room-member-changed:im.event.room-member-changed}") String topic
    ) {
        return new ImKafkaOutboxHandler<>(
                topic,
                RoomMemberChanged.class,
                objectMapper,
                kafkaTemplate
        );
    }
}
