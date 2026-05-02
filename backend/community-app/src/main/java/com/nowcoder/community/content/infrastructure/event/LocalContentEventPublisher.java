package com.nowcoder.community.content.infrastructure.event;

import com.nowcoder.community.content.application.ContentEventPublisher;
import com.nowcoder.community.content.contracts.event.CommentPayload;
import com.nowcoder.community.content.contracts.event.ContentContractEvent;
import com.nowcoder.community.content.contracts.event.ContentEventTypes;
import com.nowcoder.community.content.contracts.event.ModerationPayload;
import com.nowcoder.community.content.contracts.event.PostPayload;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@ConditionalOnProperty(name = "content.events.publisher", havingValue = "local", matchIfMissing = true)
public class LocalContentEventPublisher implements ContentEventPublisher {

    private final ApplicationEventPublisher applicationEventPublisher;

    public LocalContentEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
        this.applicationEventPublisher = applicationEventPublisher;
    }

    @Override
    public void publishPostPublished(PostPayload payload) {
        publish(ContentEventTypes.POST_PUBLISHED, payload);
    }

    @Override
    public void publishPostUpdated(PostPayload payload) {
        publish(ContentEventTypes.POST_UPDATED, payload);
    }

    @Override
    public void publishPostDeleted(PostPayload payload) {
        publish(ContentEventTypes.POST_DELETED, payload);
    }

    @Override
    public void publishCommentCreated(CommentPayload payload) {
        publish(ContentEventTypes.COMMENT_CREATED, payload);
    }

    @Override
    public void publishCommentDeleted(CommentPayload payload) {
        publish(ContentEventTypes.COMMENT_DELETED, payload);
    }

    @Override
    public void publishModerationActionApplied(ModerationPayload payload) {
        publish(ContentEventTypes.MODERATION_ACTION_APPLIED, payload);
    }

    private void publish(String type, Object payload) {
        applicationEventPublisher.publishEvent(new ContentContractEvent(UUID.randomUUID().toString(), type, payload));
    }
}
