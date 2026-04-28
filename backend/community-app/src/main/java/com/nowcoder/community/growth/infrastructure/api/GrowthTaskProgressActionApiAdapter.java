package com.nowcoder.community.growth.infrastructure.api;

import com.nowcoder.community.content.contracts.event.CommentPayload;
import com.nowcoder.community.growth.application.TaskProgressApplicationService;
import com.nowcoder.community.growth.application.command.TriggerCommentCreatedCommand;
import com.nowcoder.community.growth.application.command.TriggerLikeCreatedCommand;
import com.nowcoder.community.growth.application.command.TriggerPostPublishedCommand;
import com.nowcoder.community.growth.api.action.GrowthTaskProgressActionApi;
import com.nowcoder.community.social.contracts.event.LikePayload;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.UUID;

@Service
public class GrowthTaskProgressActionApiAdapter implements GrowthTaskProgressActionApi {

    private final TaskProgressApplicationService applicationService;

    public GrowthTaskProgressActionApiAdapter(TaskProgressApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @Override
    public void triggerPostPublished(UUID postId, UUID userId, Instant createTime) {
        if (postId == null || userId == null || createTime == null) {
            return;
        }
        applicationService.triggerPostPublished(new TriggerPostPublishedCommand(postId, userId, createTime));
    }

    @Override
    public void triggerCommentCreated(CommentPayload payload) {
        if (payload == null
                || payload.getCommentId() == null
                || payload.getUserId() == null
                || payload.getCreateTime() == null) {
            return;
        }
        applicationService.triggerCommentCreated(new TriggerCommentCreatedCommand(
                payload.getCommentId(),
                payload.getUserId(),
                payload.getCreateTime()
        ));
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
        applicationService.triggerLikeCreated(new TriggerLikeCreatedCommand(
                sourceEventId.trim(),
                payload.getActorUserId(),
                toUserId,
                payload.getCreateTime()
        ));
    }
}
