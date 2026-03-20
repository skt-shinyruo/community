package com.nowcoder.community.message.event;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.infra.outbox.OutboxEvent;
import com.nowcoder.community.infra.outbox.OutboxHandler;
import com.nowcoder.community.message.service.NoticeService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Map;

/**
 * Outbox handler for notice projection.
 */
@Component
@ConditionalOnProperty(prefix = "events.outbox", name = "enabled", havingValue = "true")
public class NoticeOutboxHandler implements OutboxHandler {

    public static final String TOPIC = "projection.notice";

    private final ObjectMapper objectMapper;
    private final NoticeService noticeService;

    public NoticeOutboxHandler(ObjectMapper objectMapper, NoticeService noticeService) {
        this.objectMapper = objectMapper;
        this.noticeService = noticeService;
    }

    @Override
    public String topic() {
        return TOPIC;
    }

    @Override
    public void handle(OutboxEvent event) {
        if (event == null || !StringUtils.hasText(event.payload())) {
            return;
        }
        NoticeOutboxPayload payload;
        try {
            payload = objectMapper.readValue(event.payload(), NoticeOutboxPayload.class);
        } catch (Exception e) {
            throw new IllegalStateException("notice outbox payload 反序列化失败", e);
        }

        int toUserId = payload.getToUserId();
        String topic = payload.getTopic();
        if (toUserId <= 0 || !StringUtils.hasText(topic)) {
            return;
        }

        String sourceEventId = payload.getSourceEventId();
        String sourceEventType = payload.getSourceEventType();
        if (!StringUtils.hasText(sourceEventId) || !StringUtils.hasText(sourceEventType)) {
            return;
        }

        String contentJson;
        try {
            contentJson = objectMapper.writeValueAsString(Map.of(
                    "eventId", sourceEventId,
                    "type", sourceEventType,
                    "payload", payload.getPayload()
            ));
        } catch (Exception e) {
            throw new IllegalStateException("notice content 序列化失败: " + sourceEventType, e);
        }

        noticeService.createNotice(toUserId, topic.trim(), contentJson);
    }

    public static class NoticeOutboxPayload {

        private int toUserId;
        private String topic;
        private String sourceEventId;
        private String sourceEventType;
        private JsonNode payload;

        public int getToUserId() {
            return toUserId;
        }

        public void setToUserId(int toUserId) {
            this.toUserId = toUserId;
        }

        public String getTopic() {
            return topic;
        }

        public void setTopic(String topic) {
            this.topic = topic;
        }

        public String getSourceEventId() {
            return sourceEventId;
        }

        public void setSourceEventId(String sourceEventId) {
            this.sourceEventId = sourceEventId;
        }

        public String getSourceEventType() {
            return sourceEventType;
        }

        public void setSourceEventType(String sourceEventType) {
            this.sourceEventType = sourceEventType;
        }

        public JsonNode getPayload() {
            return payload;
        }

        public void setPayload(JsonNode payload) {
            this.payload = payload;
        }
    }
}

