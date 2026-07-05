package com.nowcoder.community.growth.application;

import com.nowcoder.community.content.contracts.event.CommentPayload;
import com.nowcoder.community.content.contracts.event.PostPayload;
import com.nowcoder.community.social.contracts.event.LikePayload;

public interface TaskProgressIntegrationEventDispatcher {

    void dispatchPostPublished(String eventKey, PostPayload payload);

    void dispatchCommentCreated(String eventKey, CommentPayload payload);

    void dispatchLikeCreated(String eventKey, LikePayload payload);

    void dispatchLikeRemoved(String eventKey, LikePayload payload);
}
