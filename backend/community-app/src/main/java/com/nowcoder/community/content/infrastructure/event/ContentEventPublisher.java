package com.nowcoder.community.content.infrastructure.event;

import com.nowcoder.community.content.contracts.event.CommentPayload;
import com.nowcoder.community.content.contracts.event.ModerationPayload;
import com.nowcoder.community.content.contracts.event.PostPayload;

public interface ContentEventPublisher {

    void publishPostPublished(PostPayload payload);

    void publishPostUpdated(PostPayload payload);

    void publishPostDeleted(PostPayload payload);

    void publishCommentCreated(CommentPayload payload);

    void publishCommentDeleted(CommentPayload payload);

    void publishModerationActionApplied(ModerationPayload payload);
}
