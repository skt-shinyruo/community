package com.nowcoder.community.growth.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.growth.service.TaskProgressService;
import com.nowcoder.community.infra.outbox.OutboxEvent;
import com.nowcoder.community.infra.outbox.OutboxHandler;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDate;

@Component
@ConditionalOnProperty(prefix = "events.outbox", name = "enabled", havingValue = "true")
public class TaskProgressOutboxHandler implements OutboxHandler {

    public static final String TOPIC = "projection.task-progress";

    private final ObjectMapper objectMapper;
    private final TaskProgressService taskProgressService;

    public TaskProgressOutboxHandler(ObjectMapper objectMapper, TaskProgressService taskProgressService) {
        this.objectMapper = objectMapper;
        this.taskProgressService = taskProgressService;
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
        TaskProgressOutboxPayload payload;
        try {
            payload = objectMapper.readValue(event.payload(), TaskProgressOutboxPayload.class);
        } catch (Exception e) {
            throw new IllegalStateException("task progress outbox payload 反序列化失败", e);
        }
        if (payload.getUserId() <= 0 || !StringUtils.hasText(payload.getTriggerEventType()) || !StringUtils.hasText(payload.getSourceEventId()) || payload.getBizDate() == null) {
            return;
        }
        taskProgressService.processEvent(payload.getUserId(), payload.getTriggerEventType(), payload.getSourceEventId(), payload.getBizDate());
    }

    public static class TaskProgressOutboxPayload {

        private int userId;
        private String triggerEventType;
        private String sourceEventId;
        private LocalDate bizDate;

        public int getUserId() {
            return userId;
        }

        public void setUserId(int userId) {
            this.userId = userId;
        }

        public String getTriggerEventType() {
            return triggerEventType;
        }

        public void setTriggerEventType(String triggerEventType) {
            this.triggerEventType = triggerEventType;
        }

        public String getSourceEventId() {
            return sourceEventId;
        }

        public void setSourceEventId(String sourceEventId) {
            this.sourceEventId = sourceEventId;
        }

        public LocalDate getBizDate() {
            return bizDate;
        }

        public void setBizDate(LocalDate bizDate) {
            this.bizDate = bizDate;
        }
    }
}
