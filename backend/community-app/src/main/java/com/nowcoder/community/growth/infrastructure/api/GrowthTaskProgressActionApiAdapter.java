package com.nowcoder.community.growth.infrastructure.api;

import com.nowcoder.community.growth.api.action.GrowthTaskProgressActionApi;
import com.nowcoder.community.growth.api.model.GrowthCommentTaskProgressRequest;
import com.nowcoder.community.growth.api.model.GrowthLikeTaskProgressRequest;
import com.nowcoder.community.growth.application.TaskProgressApplicationService;
import com.nowcoder.community.growth.application.command.TriggerCommentCreatedCommand;
import com.nowcoder.community.growth.application.command.TriggerLikeCreatedCommand;
import com.nowcoder.community.growth.application.command.TriggerPostPublishedCommand;
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
    public void triggerCommentCreated(GrowthCommentTaskProgressRequest request) {
        if (request == null
                || request.commentId() == null
                || request.userId() == null
                || request.createTime() == null) {
            return;
        }
        applicationService.triggerCommentCreated(new TriggerCommentCreatedCommand(
                request.commentId(),
                request.userId(),
                request.createTime()
        ));
    }

    @Override
    public void triggerLikeCreated(GrowthLikeTaskProgressRequest request) {
        if (request == null || !StringUtils.hasText(request.sourceEventId()) || request.createTime() == null) {
            return;
        }
        UUID toUserId = request.entityUserId();
        if (toUserId == null || toUserId.equals(request.actorUserId())) {
            return;
        }
        applicationService.triggerLikeCreated(new TriggerLikeCreatedCommand(
                request.sourceEventId().trim(),
                request.actorUserId(),
                toUserId,
                request.createTime()
        ));
    }
}
