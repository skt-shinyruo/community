package com.nowcoder.community.growth.service;

import com.nowcoder.community.content.contracts.event.CommentPayload;
import com.nowcoder.community.content.contracts.event.ContentContractEvent;
import com.nowcoder.community.content.contracts.event.ContentEventTypes;
import com.nowcoder.community.content.contracts.event.PostPayload;
import com.nowcoder.community.growth.event.GrowthEventTypes;
import com.nowcoder.community.growth.event.GrowthLocalEvent;
import com.nowcoder.community.growth.event.payload.CheckInPayload;
import com.nowcoder.community.social.contracts.event.LikePayload;
import com.nowcoder.community.social.contracts.event.SocialContractEvent;
import com.nowcoder.community.social.contracts.event.SocialEventTypes;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;

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
        int toUserId = payload.getEntityUserId() == null ? 0 : payload.getEntityUserId();
        if (!SocialEventTypes.LIKE_CREATED.equals(event.type()) || toUserId <= 0 || toUserId == payload.getActorUserId()) {
            return null;
        }
        return new TaskProgressProjectionCommand(toUserId, event.type(), event.eventId(), toDate(payload.getCreateTime()));
    }

    public TaskProgressProjectionCommand commandForGrowthEvent(GrowthLocalEvent event) {
        if (event == null || !GrowthEventTypes.CHECK_IN_COMPLETED.equals(event.type()) || !(event.payload() instanceof CheckInPayload payload)) {
            return null;
        }
        if (payload.getUserId() <= 0 || payload.getBizDate() == null) {
            return null;
        }
        return new TaskProgressProjectionCommand(payload.getUserId(), event.type(), event.eventId(), payload.getBizDate());
    }

    public void project(TaskProgressProjectionCommand command) {
        if (command == null || command.userId() <= 0 || command.bizDate() == null) {
            return;
        }
        taskProgressService.processEvent(command.userId(), command.triggerEventType(), command.sourceEventId(), command.bizDate());
    }

    private LocalDate toDate(Instant instant) {
        return growthBusinessTimeService.dateOf(instant);
    }

    public record TaskProgressProjectionCommand(
            int userId,
            String triggerEventType,
            String sourceEventId,
            LocalDate bizDate
    ) {
    }
}
