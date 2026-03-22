package com.nowcoder.community.growth.event;

import com.nowcoder.community.content.event.ContentEventTypes;
import com.nowcoder.community.content.event.ContentLocalEvent;
import com.nowcoder.community.content.event.payload.CommentPayload;
import com.nowcoder.community.content.event.payload.PostPayload;
import com.nowcoder.community.growth.event.payload.CheckInPayload;
import com.nowcoder.community.growth.service.GrowthBusinessTimeService;
import com.nowcoder.community.growth.service.TaskProgressService;
import com.nowcoder.community.social.event.SocialEventTypes;
import com.nowcoder.community.social.event.SocialLocalEvent;
import com.nowcoder.community.social.event.payload.LikePayload;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import java.time.LocalDate;
import java.time.Instant;

@Component
@ConditionalOnProperty(prefix = "events.outbox", name = "enabled", havingValue = "false", matchIfMissing = true)
public class TaskProgressProjectionListener {

    private final TaskProgressService taskProgressService;
    private final GrowthBusinessTimeService growthBusinessTimeService;

    public TaskProgressProjectionListener(TaskProgressService taskProgressService, GrowthBusinessTimeService growthBusinessTimeService) {
        this.taskProgressService = taskProgressService;
        this.growthBusinessTimeService = growthBusinessTimeService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = false)
    public void onContentEvent(ContentLocalEvent event) {
        if (event == null) {
            return;
        }
        if (ContentEventTypes.POST_PUBLISHED.equals(event.type()) && event.payload() instanceof PostPayload payload) {
            taskProgressService.processEvent(payload.getUserId(), event.type(), event.eventId(), toDate(payload.getCreateTime()));
            return;
        }
        if (ContentEventTypes.COMMENT_CREATED.equals(event.type()) && event.payload() instanceof CommentPayload payload) {
            taskProgressService.processEvent(payload.getUserId(), event.type(), event.eventId(), toDate(payload.getCreateTime()));
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = false)
    public void onSocialEvent(SocialLocalEvent event) {
        if (event == null || !(event.payload() instanceof LikePayload payload)) {
            return;
        }
        int toUserId = payload.getEntityUserId() == null ? 0 : payload.getEntityUserId();
        if (!SocialEventTypes.LIKE_CREATED.equals(event.type()) || toUserId <= 0 || toUserId == payload.getActorUserId()) {
            return;
        }
        taskProgressService.processEvent(toUserId, event.type(), event.eventId(), toDate(payload.getCreateTime()));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = false)
    public void onGrowthEvent(GrowthLocalEvent event) {
        if (event == null || !GrowthEventTypes.CHECK_IN_COMPLETED.equals(event.type()) || !(event.payload() instanceof CheckInPayload payload)) {
            return;
        }
        if (payload.getUserId() <= 0 || payload.getBizDate() == null) {
            return;
        }
        taskProgressService.processEvent(payload.getUserId(), event.type(), event.eventId(), payload.getBizDate());
    }

    private LocalDate toDate(Instant instant) {
        return growthBusinessTimeService.dateOf(instant);
    }
}
