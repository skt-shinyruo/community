package com.nowcoder.community.message.kafka;

import com.nowcoder.community.common.kafka.KafkaTraceSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.content.api.event.ContentEventTopics;
import com.nowcoder.community.social.api.event.SocialEventTopics;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
public class NoticeEventConsumer {

    private final ObjectMapper objectMapper;
    private final NoticeEventProcessor processor;

    public NoticeEventConsumer(ObjectMapper objectMapper, NoticeEventProcessor processor) {
        this.objectMapper = objectMapper;
        this.processor = processor;
    }

    @KafkaListener(topics = {ContentEventTopics.COMMENT_EVENTS_V1, SocialEventTopics.SOCIAL_EVENTS_V1, ContentEventTopics.MODERATION_EVENTS_V1}, groupId = "message-service")
    public void onMessage(ConsumerRecord<String, String> record, Acknowledgment ack) {
        KafkaTraceSupport.runWithTraceId(objectMapper, record.value(), () -> processor.handleRecord(record));
        ack.acknowledge();
    }
}
