package com.nowcoder.community.message.kafka;

import com.nowcoder.community.common.event.EventTopics;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
public class NoticeEventConsumer {

    private final NoticeEventProcessor processor;

    public NoticeEventConsumer(NoticeEventProcessor processor) {
        this.processor = processor;
    }

    @KafkaListener(topics = {EventTopics.COMMENT_EVENTS_V1, EventTopics.SOCIAL_EVENTS_V1}, groupId = "message-service")
    public void onMessage(ConsumerRecord<String, String> record, Acknowledgment ack) throws Exception {
        processor.handleRecord(record);
        ack.acknowledge();
    }
}
