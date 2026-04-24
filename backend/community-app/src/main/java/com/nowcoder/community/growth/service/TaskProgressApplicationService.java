package com.nowcoder.community.growth.service;

import com.nowcoder.community.content.contracts.event.CommentPayload;
import com.nowcoder.community.content.contracts.event.ContentEventTypes;
import com.nowcoder.community.growth.api.action.GrowthTaskProgressActionApi;
import com.nowcoder.community.social.contracts.event.LikePayload;
import com.nowcoder.community.social.contracts.event.SocialEventTypes;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Service
public class TaskProgressApplicationService implements GrowthTaskProgressActionApi {

    private final TaskProgressService taskProgressService;
    private final GrowthBusinessTimeService growthBusinessTimeService;

    public TaskProgressApplicationService(
            TaskProgressService taskProgressService,
            GrowthBusinessTimeService growthBusinessTimeService
    ) {
        this.taskProgressService = taskProgressService;
        this.growthBusinessTimeService = growthBusinessTimeService;
    }

    @Override
    public void triggerPostPublished(UUID postId, UUID userId, Instant createTime) {
        if (postId == null || userId == null || createTime == null) {
            return;
        }
        process(userId, ContentEventTypes.POST_PUBLISHED, "post-published:" + postId, createTime);
    }

    @Override
    public void triggerCommentCreated(CommentPayload payload) {
        if (payload == null
                || payload.getCommentId() == null
                || payload.getUserId() == null
                || payload.getCreateTime() == null) {
            return;
        }
        process(
                payload.getUserId(),
                ContentEventTypes.COMMENT_CREATED,
                "comment-created:" + payload.getCommentId(),
                payload.getCreateTime()
        );
    }

    @Override
    public void triggerLikeCreated(String sourceEventId, LikePayload payload) {
        if (!StringUtils.hasText(sourceEventId) || payload == null || payload.getCreateTime() == null) {
            return;
        }
        UUID toUserId = payload.getEntityUserId();
        if (toUserId == null || toUserId.equals(payload.getActorUserId())) {
            return;
        }
        process(toUserId, SocialEventTypes.LIKE_CREATED, sourceEventId.trim(), payload.getCreateTime());
    }

    private void process(UUID userId, String triggerEventType, String sourceEventId, Instant occurredAt) {
        if (userId == null || !StringUtils.hasText(triggerEventType) || !StringUtils.hasText(sourceEventId) || occurredAt == null) {
            return;
        }
        LocalDate bizDate = growthBusinessTimeService.dateOf(occurredAt);
        if (bizDate == null) {
            return;
        }
        taskProgressService.processEvent(userId, triggerEventType, sourceEventId, bizDate);
    }
}
