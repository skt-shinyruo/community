package com.nowcoder.community.growth.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.growth.service.GrowthBusinessTimeService;
import com.nowcoder.community.growth.service.TaskProgressProjectionService;
import com.nowcoder.community.growth.service.TaskProgressService;
import com.nowcoder.community.common.outbox.OutboxEvent;
import com.nowcoder.community.common.outbox.OutboxHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.ZoneId;

@Component
@ConditionalOnProperty(prefix = "events.outbox", name = "enabled", havingValue = "true")
public class TaskProgressOutboxHandler implements OutboxHandler {

    public static final String TOPIC = "projection.task-progress";

    private final ObjectMapper objectMapper;
    private final TaskProgressProjectionService taskProgressProjectionService;

    @Autowired
    public TaskProgressOutboxHandler(ObjectMapper objectMapper, TaskProgressProjectionService taskProgressProjectionService) {
        this.objectMapper = objectMapper;
        this.taskProgressProjectionService = taskProgressProjectionService;
    }

    TaskProgressOutboxHandler(ObjectMapper objectMapper, TaskProgressService taskProgressService) {
        this(
                objectMapper,
                new TaskProgressProjectionService(
                        taskProgressService,
                        new GrowthBusinessTimeService(Clock.systemUTC(), ZoneId.of("UTC"))
                )
        );
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
        TaskProgressProjectionService.TaskProgressProjectionCommand payload;
        try {
            payload = objectMapper.readValue(event.payload(), TaskProgressProjectionService.TaskProgressProjectionCommand.class);
        } catch (Exception e) {
            throw new IllegalStateException("task progress outbox payload 反序列化失败", e);
        }
        taskProgressProjectionService.project(payload);
    }
}
