package com.nowcoder.community.growth.infrastructure.event;

import com.fasterxml.jackson.databind.JsonNode;
import com.nowcoder.community.common.json.JsonCodec;
import com.nowcoder.community.content.contracts.event.CommentPayload;
import com.nowcoder.community.content.contracts.event.ContentContractEvent;
import com.nowcoder.community.content.contracts.event.ContentEventTypes;
import com.nowcoder.community.content.contracts.event.PostPayload;
import com.nowcoder.community.growth.application.TaskProgressApplicationService;
import com.nowcoder.community.growth.application.command.TriggerCommentCreatedCommand;
import com.nowcoder.community.growth.application.command.TriggerLikeCreatedCommand;
import com.nowcoder.community.growth.application.command.TriggerPostPublishedCommand;
import com.nowcoder.community.social.contracts.event.LikePayload;
import com.nowcoder.community.social.contracts.event.SocialContractEvent;
import com.nowcoder.community.social.contracts.event.SocialEventTypes;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Component
public class TaskProgressEventBackboneKafkaListener {

    private final JsonCodec jsonCodec;
    private final TaskProgressApplicationService applicationService;

    public TaskProgressEventBackboneKafkaListener(
            JsonCodec jsonCodec,
            TaskProgressApplicationService applicationService
    ) {
        this.jsonCodec = jsonCodec;
        this.applicationService = applicationService;
    }

    @KafkaListener(
            topics = "${content.events.kafka-topic:content.events}",
            groupId = "${growth.task.kafka.consumer.group-id:growth-task-progress}",
            concurrency = "${growth.task.kafka.consumer.concurrency:3}"
    )
    public void onContentEvent(ContentContractEvent event) {
        if (event == null) {
            return;
        }
        if (ContentEventTypes.POST_PUBLISHED.equals(event.type())) {
            handlePostPublished(event.payload());
            return;
        }
        if (ContentEventTypes.COMMENT_CREATED.equals(event.type())) {
            handleCommentCreated(event.payload());
        }
    }

    @KafkaListener(
            topics = "${social.events.kafka-topic:social.events}",
            groupId = "${growth.task.kafka.consumer.group-id:growth-task-progress}",
            concurrency = "${growth.task.kafka.consumer.concurrency:3}"
    )
    public void onSocialEvent(SocialContractEvent event) {
        if (event == null || !SocialEventTypes.LIKE_CREATED.equals(event.type())) {
            return;
        }
        LikePayload payload = normalizePayload(event.payload(), LikePayload.class);
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

    private void handlePostPublished(Object eventPayload) {
        PostPayload payload = normalizePayload(eventPayload, PostPayload.class);
        if (payload == null || payload.getPostId() == null || payload.getUserId() == null || payload.getCreateTime() == null) {
            return;
        }
        applicationService.triggerPostPublished(new TriggerPostPublishedCommand(
                payload.getPostId(),
                payload.getUserId(),
                payload.getCreateTime()
        ));
    }

    private void handleCommentCreated(Object eventPayload) {
        CommentPayload payload = normalizePayload(eventPayload, CommentPayload.class);
        if (payload == null || payload.getCommentId() == null || payload.getUserId() == null || payload.getCreateTime() == null) {
            return;
        }
        applicationService.triggerCommentCreated(new TriggerCommentCreatedCommand(
                payload.getCommentId(),
                payload.getUserId(),
                payload.getCreateTime()
        ));
    }

    private String sourceEventId(LikePayload payload) {
        String source = payload.getActorUserId() + ":" + payload.getEntityType() + ":" + payload.getEntityId();
        UUID digest = UUID.nameUUIDFromBytes(source.getBytes(StandardCharsets.UTF_8));
        return "gl:like:" + digest;
    }

    private <T> T normalizePayload(Object payload, Class<T> type) {
        if (payload == null || type.isInstance(payload)) {
            return type.cast(payload);
        }
        JsonNode node = jsonCodec.valueToTree(payload);
        return jsonCodec.treeToValue(node, type);
    }
}
