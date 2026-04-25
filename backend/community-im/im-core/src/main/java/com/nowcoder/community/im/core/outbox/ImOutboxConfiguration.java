package com.nowcoder.community.im.core.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.common.outbox.OutboxHandler;
import com.nowcoder.community.im.common.ImTopics;
import com.nowcoder.community.im.common.event.PrivateMessagePersistedEvent;
import com.nowcoder.community.im.common.event.PrivateMessageRejectedEvent;
import com.nowcoder.community.im.common.event.RoomMemberChanged;
import com.nowcoder.community.im.common.event.RoomMessagePersistedEvent;
import com.nowcoder.community.im.common.event.RoomMessageRejectedEvent;
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
            KafkaTemplate<String, Object> kafkaTemplate
    ) {
        return new ImKafkaOutboxHandler<>(
                ImTopics.EVENT_PRIVATE_PERSISTED,
                PrivateMessagePersistedEvent.class,
                objectMapper,
                kafkaTemplate
        );
    }

    @Bean
    public OutboxHandler imRoomPersistedKafkaOutboxHandler(
            ObjectMapper objectMapper,
            KafkaTemplate<String, Object> kafkaTemplate
    ) {
        return new ImKafkaOutboxHandler<>(
                ImTopics.EVENT_ROOM_PERSISTED,
                RoomMessagePersistedEvent.class,
                objectMapper,
                kafkaTemplate
        );
    }

    @Bean
    public OutboxHandler imPrivateRejectedKafkaOutboxHandler(
            ObjectMapper objectMapper,
            KafkaTemplate<String, Object> kafkaTemplate
    ) {
        return new ImKafkaOutboxHandler<>(
                ImTopics.EVENT_PRIVATE_REJECTED,
                PrivateMessageRejectedEvent.class,
                objectMapper,
                kafkaTemplate
        );
    }

    @Bean
    public OutboxHandler imRoomRejectedKafkaOutboxHandler(
            ObjectMapper objectMapper,
            KafkaTemplate<String, Object> kafkaTemplate
    ) {
        return new ImKafkaOutboxHandler<>(
                ImTopics.EVENT_ROOM_REJECTED,
                RoomMessageRejectedEvent.class,
                objectMapper,
                kafkaTemplate
        );
    }

    @Bean
    public OutboxHandler imRoomMemberChangedKafkaOutboxHandler(
            ObjectMapper objectMapper,
            KafkaTemplate<String, Object> kafkaTemplate
    ) {
        return new ImKafkaOutboxHandler<>(
                ImTopics.EVENT_ROOM_MEMBER_CHANGED,
                RoomMemberChanged.class,
                objectMapper,
                kafkaTemplate
        );
    }
}
