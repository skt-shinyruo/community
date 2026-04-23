package com.nowcoder.community.growth.service;

import com.nowcoder.community.content.contracts.event.CommentPayload;
import com.nowcoder.community.content.contracts.event.ContentContractEvent;
import com.nowcoder.community.content.contracts.event.ContentEventTypes;
import com.nowcoder.community.content.contracts.event.PostPayload;
import com.nowcoder.community.social.contracts.event.LikePayload;
import com.nowcoder.community.social.contracts.event.SocialContractEvent;
import com.nowcoder.community.social.contracts.event.SocialEventTypes;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.UUID;

@Service
public class TaskProgressTriggerService {

    private final TaskProgressProjectionService taskProgressProjectionService;

    public TaskProgressTriggerService(TaskProgressProjectionService taskProgressProjectionService) {
        this.taskProgressProjectionService = taskProgressProjectionService;
    }

    public void triggerPostPublished(UUID postId, UUID userId, Instant createTime) {
        if (postId == null || userId == null || createTime == null) {
            return;
        }
        PostPayload payload = new PostPayload();
        payload.setPostId(postId);
        payload.setUserId(userId);
        payload.setCreateTime(createTime);
        taskProgressProjectionService.project(taskProgressProjectionService.commandForContentEvent(
                new ContentContractEvent("post-published:" + postId, ContentEventTypes.POST_PUBLISHED, payload)
        ));
    }

    public void triggerCommentCreated(CommentPayload payload) {
        if (payload == null || payload.getCommentId() == null) {
            return;
        }
        taskProgressProjectionService.project(taskProgressProjectionService.commandForContentEvent(
                new ContentContractEvent("comment-created:" + payload.getCommentId(), ContentEventTypes.COMMENT_CREATED, payload)
        ));
    }

    public void triggerLikeCreated(String sourceEventId, LikePayload payload) {
        if (!StringUtils.hasText(sourceEventId) || payload == null) {
            return;
        }
        taskProgressProjectionService.project(taskProgressProjectionService.commandForSocialEvent(
                new SocialContractEvent(sourceEventId, SocialEventTypes.LIKE_CREATED, payload)
        ));
    }
}
