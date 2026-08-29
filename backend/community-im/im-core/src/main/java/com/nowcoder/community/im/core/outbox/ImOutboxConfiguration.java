package com.nowcoder.community.im.core.outbox;

import com.nowcoder.community.common.json.JacksonJsonCodec;
import com.nowcoder.community.common.outbox.OutboxHandler;
import com.nowcoder.community.im.common.event.PrivateMessageCommittedEvent;
import com.nowcoder.community.im.common.event.PrivateMessagePersistedEvent;
import com.nowcoder.community.im.common.event.PrivateMessageRejectedEvent;
import com.nowcoder.community.im.common.event.RoomMemberChanged;
import com.nowcoder.community.im.common.event.RoomMessageCommittedEvent;
import com.nowcoder.community.im.common.event.RoomMessagePersistedEvent;
import com.nowcoder.community.im.common.event.RoomMessageRejectedEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;

@Configuration
@ConditionalOnProperty(prefix = "events.outbox", name = "enabled", havingValue = "true")
public class ImOutboxConfiguration {

    @Bean
    public OutboxHandler imPrivatePersistedKafkaOutboxHandler(
            JacksonJsonCodec jsonCodec,
            KafkaTemplate<String, Object> kafkaTemplate,
            @Value("${im.kafka.topics.event-private-persisted:im.event.private-persisted}") String topic
    ) {
        return new ImKafkaOutboxHandler<>(
                topic,
                PrivateMessagePersistedEvent.class,
                jsonCodec,
                kafkaTemplate
        );
    }

    @Bean
    public OutboxHandler imRoomPersistedKafkaOutboxHandler(
            JacksonJsonCodec jsonCodec,
            KafkaTemplate<String, Object> kafkaTemplate,
            @Value("${im.kafka.topics.event-room-persisted:im.event.room-persisted}") String topic
    ) {
        return new ImKafkaOutboxHandler<>(
                topic,
                RoomMessagePersistedEvent.class,
                jsonCodec,
                kafkaTemplate
        );
    }

    @Bean
    public OutboxHandler imPrivateCommittedKafkaOutboxHandler(
            JacksonJsonCodec jsonCodec,
            KafkaTemplate<String, Object> kafkaTemplate,
            @Value("${im.kafka.topics.event-private-committed:im.event.private-committed}") String topic
    ) {
        return new ImKafkaOutboxHandler<>(
                topic,
                PrivateMessageCommittedEvent.class,
                jsonCodec,
                kafkaTemplate
        );
    }

    @Bean
    public OutboxHandler imRoomCommittedKafkaOutboxHandler(
            JacksonJsonCodec jsonCodec,
            KafkaTemplate<String, Object> kafkaTemplate,
            @Value("${im.kafka.topics.event-room-committed:im.event.room-committed}") String topic
    ) {
        return new ImKafkaOutboxHandler<>(
                topic,
                RoomMessageCommittedEvent.class,
                jsonCodec,
                kafkaTemplate
        );
    }

    @Bean
    public OutboxHandler imPrivateRejectedKafkaOutboxHandler(
            JacksonJsonCodec jsonCodec,
            KafkaTemplate<String, Object> kafkaTemplate,
            @Value("${im.kafka.topics.event-private-rejected:im.event.private-rejected}") String topic
    ) {
        return new ImKafkaOutboxHandler<>(
                topic,
                PrivateMessageRejectedEvent.class,
                jsonCodec,
                kafkaTemplate
        );
    }

    @Bean
    public OutboxHandler imRoomRejectedKafkaOutboxHandler(
            JacksonJsonCodec jsonCodec,
            KafkaTemplate<String, Object> kafkaTemplate,
            @Value("${im.kafka.topics.event-room-rejected:im.event.room-rejected}") String topic
    ) {
        return new ImKafkaOutboxHandler<>(
                topic,
                RoomMessageRejectedEvent.class,
                jsonCodec,
                kafkaTemplate
        );
    }

    @Bean
    public OutboxHandler imRoomMemberChangedKafkaOutboxHandler(
            JacksonJsonCodec jsonCodec,
            KafkaTemplate<String, Object> kafkaTemplate,
            @Value("${im.kafka.topics.event-room-member-changed:im.event.room-member-changed}") String topic
    ) {
        return new ImKafkaOutboxHandler<>(
                topic,
                RoomMemberChanged.class,
                jsonCodec,
                kafkaTemplate
        );
    }
}
