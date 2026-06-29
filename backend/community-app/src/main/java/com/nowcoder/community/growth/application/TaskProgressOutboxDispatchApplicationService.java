package com.nowcoder.community.growth.application;

import com.nowcoder.community.common.json.JsonCodec;
import com.nowcoder.community.common.json.JsonCodecException;
import com.nowcoder.community.content.contracts.event.CommentPayload;
import com.nowcoder.community.content.contracts.event.PostPayload;
import com.nowcoder.community.social.contracts.event.LikePayload;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class TaskProgressOutboxDispatchApplicationService {

    private final JsonCodec jsonCodec;
    private final TaskProgressKafkaDispatchPort dispatchPort;
    private final String postPublishedTopic;
    private final String commentCreatedTopic;
    private final String likeCreatedTopic;
    private final String likeRemovedTopic;

    public TaskProgressOutboxDispatchApplicationService(
            JsonCodec jsonCodec,
            TaskProgressKafkaDispatchPort dispatchPort,
            @Value("${growth.task.kafka.topics.post-published:growth.task.post-published}") String postPublishedTopic,
            @Value("${growth.task.kafka.topics.comment-created:growth.task.comment-created}") String commentCreatedTopic,
            @Value("${growth.task.kafka.topics.like-created:growth.task.like-created}") String likeCreatedTopic,
            @Value("${growth.task.kafka.topics.like-removed:growth.task.like-removed}") String likeRemovedTopic
    ) {
        this.jsonCodec = jsonCodec;
        this.dispatchPort = dispatchPort;
        this.postPublishedTopic = postPublishedTopic;
        this.commentCreatedTopic = commentCreatedTopic;
        this.likeCreatedTopic = likeCreatedTopic;
        this.likeRemovedTopic = likeRemovedTopic;
    }

    public void dispatchPostPublished(String key, String payloadJson) {
        if (!StringUtils.hasText(payloadJson)) {
            return;
        }
        PostPayload payload;
        try {
            payload = jsonCodec.fromJson(payloadJson, PostPayload.class);
        } catch (JsonCodecException e) {
            throw new IllegalStateException("growth task post outbox payload 反序列化失败", e);
        }
        if (payload.getPostId() == null || payload.getUserId() == null || payload.getCreateTime() == null) {
            return;
        }
        dispatchPort.send(postPublishedTopic, dispatchKey(key, payload.getUserId().toString()), payload);
    }

    public void dispatchCommentCreated(String key, String payloadJson) {
        if (!StringUtils.hasText(payloadJson)) {
            return;
        }
        CommentPayload payload;
        try {
            payload = jsonCodec.fromJson(payloadJson, CommentPayload.class);
        } catch (JsonCodecException e) {
            throw new IllegalStateException("growth task comment outbox payload 反序列化失败", e);
        }
        if (payload.getCommentId() == null || payload.getUserId() == null || payload.getCreateTime() == null) {
            return;
        }
        dispatchPort.send(commentCreatedTopic, dispatchKey(key, payload.getUserId().toString()), payload);
    }

    public void dispatchLikeCreated(String key, String payloadJson) {
        if (!StringUtils.hasText(payloadJson)) {
            return;
        }
        LikePayload payload;
        try {
            payload = jsonCodec.fromJson(payloadJson, LikePayload.class);
        } catch (JsonCodecException e) {
            throw new IllegalStateException("growth task like outbox payload 反序列化失败", e);
        }
        if (payload.getActorUserId() == null
                || payload.getEntityId() == null
                || payload.getEntityUserId() == null
                || payload.getCreateTime() == null
                || payload.getActorUserId().equals(payload.getEntityUserId())) {
            return;
        }
        dispatchPort.send(likeCreatedTopic, dispatchKey(key, payload.getEntityUserId().toString()), payload);
    }

    public void dispatchLikeRemoved(String key, String payloadJson) {
        if (!StringUtils.hasText(payloadJson)) {
            return;
        }
        LikePayload payload;
        try {
            payload = jsonCodec.fromJson(payloadJson, LikePayload.class);
        } catch (JsonCodecException e) {
            throw new IllegalStateException("growth task like outbox payload 反序列化失败", e);
        }
        if (payload.getEntityUserId() == null || !StringUtils.hasText(payload.getRelationKey())) {
            return;
        }
        dispatchPort.send(likeRemovedTopic, dispatchKey(key, payload.getEntityUserId().toString()), payload);
    }

    private String dispatchKey(String eventKey, String fallback) {
        return StringUtils.hasText(eventKey) ? eventKey.trim() : fallback;
    }
}
