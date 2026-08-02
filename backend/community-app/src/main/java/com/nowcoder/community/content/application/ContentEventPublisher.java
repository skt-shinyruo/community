package com.nowcoder.community.content.application;

import com.nowcoder.community.content.contracts.event.CommentPayload;
import com.nowcoder.community.content.contracts.event.ModerationPayload;
import com.nowcoder.community.content.contracts.event.PostPayload;
import com.nowcoder.community.content.contracts.event.PostScorePayload;

public interface ContentEventPublisher {

    void publishPostPublished(PostPayload payload);

    void publishPostUpdated(PostPayload payload);

    void publishPostScoreUpdated(PostScorePayload payload);

    void publishPostDeleted(PostPayload payload);

    void publishCommentCreated(CommentPayload payload);

    void publishCommentDeleted(CommentPayload payload);

    void publishModerationActionApplied(ModerationPayload payload);
}
