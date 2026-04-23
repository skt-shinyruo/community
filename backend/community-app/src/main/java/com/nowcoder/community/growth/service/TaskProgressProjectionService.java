package com.nowcoder.community.growth.service;

import com.nowcoder.community.content.contracts.event.CommentPayload;
import com.nowcoder.community.content.contracts.event.ContentContractEvent;
import com.nowcoder.community.content.contracts.event.ContentEventTypes;
import com.nowcoder.community.content.contracts.event.PostPayload;
import com.nowcoder.community.social.contracts.event.LikePayload;
import com.nowcoder.community.social.contracts.event.SocialContractEvent;
import com.nowcoder.community.social.contracts.event.SocialEventTypes;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Service
public class TaskProgressProjectionService {

    private final TaskProgressService taskProgressService;
    private final GrowthBusinessTimeService growthBusinessTimeService;

    public TaskProgressProjectionService(TaskProgressService taskProgressService, GrowthBusinessTimeService growthBusinessTimeService) {
        this.taskProgressService = taskProgressService;
        this.growthBusinessTimeService = growthBusinessTimeService;
    }

    public TaskProgressProjectionCommand commandForContentEvent(ContentContractEvent event) {
        if (event == null) {
            return null;
        }
        if (ContentEventTypes.POST_PUBLISHED.equals(event.type()) && event.payload() instanceof PostPayload payload) {
            return new TaskProgressProjectionCommand(payload.getUserId(), event.type(), event.eventId(), toDate(payload.getCreateTime()));
        }
        if (ContentEventTypes.COMMENT_CREATED.equals(event.type()) && event.payload() instanceof CommentPayload payload) {
            return new TaskProgressProjectionCommand(payload.getUserId(), event.type(), event.eventId(), toDate(payload.getCreateTime()));
        }
        return null;
    }

    public TaskProgressProjectionCommand commandForSocialEvent(SocialContractEvent event) {
        if (event == null || !(event.payload() instanceof LikePayload payload)) {
            return null;
        }
        UUID toUserId = payload.getEntityUserId();
        if (!SocialEventTypes.LIKE_CREATED.equals(event.type()) || toUserId == null || toUserId.equals(payload.getActorUserId())) {
            return null;
        }
        return new TaskProgressProjectionCommand(toUserId, event.type(), event.eventId(), toDate(payload.getCreateTime()));
    }

    public void project(TaskProgressProjectionCommand command) {
        if (command == null || command.userId() == null || command.bizDate() == null) {
            return;
        }
        taskProgressService.processEvent(command.userId(), command.triggerEventType(), command.sourceEventId(), command.bizDate());
    }

    private LocalDate toDate(Instant instant) {
        return growthBusinessTimeService.dateOf(instant);
    }

    public record TaskProgressProjectionCommand(
            UUID userId,
            String triggerEventType,
            String sourceEventId,
            LocalDate bizDate
    ) {
    }
}
