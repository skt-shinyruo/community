package com.nowcoder.community.growth.infrastructure.event;

import com.nowcoder.community.content.contracts.event.CommentPayload;
import com.nowcoder.community.content.contracts.event.PostPayload;
import com.nowcoder.community.growth.application.TaskProgressApplicationService;
import com.nowcoder.community.growth.application.command.TriggerCommentCreatedCommand;
import com.nowcoder.community.growth.application.command.TriggerLikeCreatedCommand;
import com.nowcoder.community.growth.application.command.TriggerPostPublishedCommand;
import com.nowcoder.community.social.contracts.event.LikePayload;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class TaskProgressKafkaListener {

    private final TaskProgressApplicationService applicationService;

    public TaskProgressKafkaListener(TaskProgressApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @KafkaListener(
            topics = "${growth.task.kafka.topics.post-published:growth.task.post-published}",
            groupId = "${growth.task.kafka.consumer.group-id:growth-task-progress}",
            concurrency = "${growth.task.kafka.consumer.concurrency:3}"
    )
    public void onPostPublished(PostPayload payload) {
        if (payload == null || payload.getPostId() == null || payload.getUserId() == null || payload.getCreateTime() == null) {
            return;
        }
        applicationService.triggerPostPublished(new TriggerPostPublishedCommand(
                payload.getPostId(),
                payload.getUserId(),
                payload.getCreateTime()
        ));
    }

    @KafkaListener(
            topics = "${growth.task.kafka.topics.comment-created:growth.task.comment-created}",
            groupId = "${growth.task.kafka.consumer.group-id:growth-task-progress}",
            concurrency = "${growth.task.kafka.consumer.concurrency:3}"
    )
    public void onCommentCreated(CommentPayload payload) {
        if (payload == null || payload.getCommentId() == null || payload.getUserId() == null || payload.getCreateTime() == null) {
            return;
        }
        applicationService.triggerCommentCreated(new TriggerCommentCreatedCommand(
                payload.getCommentId(),
                payload.getUserId(),
                payload.getCreateTime()
        ));
    }

    @KafkaListener(
            topics = "${growth.task.kafka.topics.like-created:growth.task.like-created}",
            groupId = "${growth.task.kafka.consumer.group-id:growth-task-progress}",
            concurrency = "${growth.task.kafka.consumer.concurrency:3}"
    )
    public void onLikeCreated(LikePayload payload) {
        if (payload == null
                || payload.getActorUserId() == null
                || payload.getEntityId() == null
                || payload.getEntityUserId() == null
                || payload.getCreateTime() == null
                || payload.getActorUserId().equals(payload.getEntityUserId())) {
            return;
        }
        applicationService.triggerLikeCreated(new TriggerLikeCreatedCommand(
                sourceEventId(payload),
                payload.getActorUserId(),
                payload.getEntityUserId(),
                payload.getCreateTime()
        ));
    }

    private String sourceEventId(LikePayload payload) {
        return "like-created:" + payload.getActorUserId() + ":" + payload.getEntityType() + ":" + payload.getEntityId();
    }
}
