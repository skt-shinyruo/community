package com.nowcoder.community.growth.event;

import com.nowcoder.community.content.contracts.event.ContentContractEvent;
import com.nowcoder.community.growth.service.GrowthBusinessTimeService;
import com.nowcoder.community.growth.service.TaskProgressProjectionService;
import com.nowcoder.community.growth.service.TaskProgressService;
import com.nowcoder.community.social.contracts.event.SocialContractEvent;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@ConditionalOnProperty(prefix = "events.outbox", name = "enabled", havingValue = "false", matchIfMissing = true)
public class TaskProgressProjectionListener {

    private final TaskProgressProjectionService taskProgressProjectionService;

    public TaskProgressProjectionListener(TaskProgressProjectionService taskProgressProjectionService) {
        this.taskProgressProjectionService = taskProgressProjectionService;
    }

    TaskProgressProjectionListener(TaskProgressService taskProgressService, GrowthBusinessTimeService growthBusinessTimeService) {
        this(new TaskProgressProjectionService(taskProgressService, growthBusinessTimeService));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = false)
    public void onContentEvent(ContentContractEvent event) {
        taskProgressProjectionService.project(taskProgressProjectionService.commandForContentEvent(event));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = false)
    public void onSocialEvent(SocialContractEvent event) {
        taskProgressProjectionService.project(taskProgressProjectionService.commandForSocialEvent(event));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = false)
    public void onGrowthEvent(GrowthLocalEvent event) {
        taskProgressProjectionService.project(taskProgressProjectionService.commandForGrowthEvent(event));
    }
}
