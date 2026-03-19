package com.nowcoder.community.content.event;

import com.nowcoder.community.content.event.payload.CommentPayload;
import com.nowcoder.community.content.event.payload.ModerationCommandPayload;
import com.nowcoder.community.content.event.payload.ModerationPayload;
import com.nowcoder.community.content.event.payload.PostPayload;

public interface ContentEventPublisher {

    void publishPostPublished(PostPayload payload);

    void publishPostUpdated(PostPayload payload);

    void publishPostDeleted(PostPayload payload);

    void publishCommentCreated(CommentPayload payload);

    void publishCommentDeleted(CommentPayload payload);

    void publishModerationActionApplied(ModerationPayload payload);

    void publishModerationCommandRequested(ModerationCommandPayload payload);
}
