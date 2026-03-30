package com.nowcoder.community.growth.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.content.contracts.event.ContentContractEvent;
import com.nowcoder.community.growth.service.GrowthBusinessTimeService;
import com.nowcoder.community.growth.service.TaskProgressProjectionService;
import com.nowcoder.community.growth.service.TaskProgressService;
import com.nowcoder.community.infra.outbox.JdbcOutboxEventStore;
import com.nowcoder.community.social.contracts.event.SocialContractEvent;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@ConditionalOnProperty(prefix = "events.outbox", name = "enabled", havingValue = "true")
public class TaskProgressOutboxEnqueuer {

    private static final String OUTBOX_SUFFIX = ":task-progress";

    private final ObjectMapper objectMapper;
    private final JdbcOutboxEventStore store;
    private final TaskProgressProjectionService taskProgressProjectionService;

    public TaskProgressOutboxEnqueuer(
            ObjectMapper objectMapper,
            JdbcOutboxEventStore store,
            TaskProgressProjectionService taskProgressProjectionService
    ) {
        this.objectMapper = objectMapper;
        this.store = store;
        this.taskProgressProjectionService = taskProgressProjectionService;
    }

    TaskProgressOutboxEnqueuer(
            ObjectMapper objectMapper,
            JdbcOutboxEventStore store,
            GrowthBusinessTimeService growthBusinessTimeService
    ) {
        this(objectMapper, store, new TaskProgressProjectionService((TaskProgressService) null, growthBusinessTimeService));
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT, fallbackExecution = false)
    public void onContentEvent(ContentContractEvent event) {
        enqueue(taskProgressProjectionService.commandForContentEvent(event));
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT, fallbackExecution = false)
    public void onSocialEvent(SocialContractEvent event) {
        enqueue(taskProgressProjectionService.commandForSocialEvent(event));
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT, fallbackExecution = false)
    public void onGrowthEvent(GrowthLocalEvent event) {
        enqueue(taskProgressProjectionService.commandForGrowthEvent(event));
    }

    private void enqueue(TaskProgressProjectionService.TaskProgressProjectionCommand command) {
        if (command == null || command.userId() <= 0 || command.bizDate() == null) {
            return;
        }
        if (command.sourceEventId() == null || command.sourceEventId().isBlank()) {
            return;
        }
        if (command.triggerEventType() == null || command.triggerEventType().isBlank()) {
            return;
        }
        String json;
        try {
            json = objectMapper.writeValueAsString(command);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("task progress outbox payload 序列化失败", e);
        }
        store.enqueue(command.sourceEventId() + OUTBOX_SUFFIX, TaskProgressOutboxHandler.TOPIC, String.valueOf(command.userId()), json);
    }
}
