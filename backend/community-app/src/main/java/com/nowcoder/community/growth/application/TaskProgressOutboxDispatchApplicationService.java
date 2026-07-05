package com.nowcoder.community.growth.application;

import com.nowcoder.community.common.json.JsonCodec;
import com.nowcoder.community.common.json.JsonCodecException;
import com.nowcoder.community.content.contracts.event.CommentPayload;
import com.nowcoder.community.content.contracts.event.PostPayload;
import com.nowcoder.community.growth.application.command.DispatchTaskProgressEventCommand;
import com.nowcoder.community.social.contracts.event.LikePayload;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class TaskProgressOutboxDispatchApplicationService {

    private final JsonCodec jsonCodec;
    private final TaskProgressIntegrationEventDispatcher dispatcher;

    public TaskProgressOutboxDispatchApplicationService(
            JsonCodec jsonCodec,
            TaskProgressIntegrationEventDispatcher dispatcher
    ) {
        this.jsonCodec = jsonCodec;
        this.dispatcher = dispatcher;
    }

    public void dispatch(DispatchTaskProgressEventCommand command) {
        if (command == null || command.kind() == null || !StringUtils.hasText(command.payloadJson())) {
            return;
        }
        switch (command.kind()) {
            case POST_PUBLISHED -> dispatchPostPublished(command.eventKey(), command.payloadJson());
            case COMMENT_CREATED -> dispatchCommentCreated(command.eventKey(), command.payloadJson());
            case LIKE_CREATED -> dispatchLikeCreated(command.eventKey(), command.payloadJson());
            case LIKE_REMOVED -> dispatchLikeRemoved(command.eventKey(), command.payloadJson());
        }
    }

    private void dispatchPostPublished(String key, String payloadJson) {
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
        dispatcher.dispatchPostPublished(dispatchKey(key, payload.getUserId().toString()), payload);
    }

    private void dispatchCommentCreated(String key, String payloadJson) {
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
        dispatcher.dispatchCommentCreated(dispatchKey(key, payload.getUserId().toString()), payload);
    }

    private void dispatchLikeCreated(String key, String payloadJson) {
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
        dispatcher.dispatchLikeCreated(dispatchKey(key, payload.getEntityUserId().toString()), payload);
    }

    private void dispatchLikeRemoved(String key, String payloadJson) {
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
        dispatcher.dispatchLikeRemoved(dispatchKey(key, payload.getEntityUserId().toString()), payload);
    }

    private String dispatchKey(String eventKey, String fallback) {
        return StringUtils.hasText(eventKey) ? eventKey.trim() : fallback;
    }
}
