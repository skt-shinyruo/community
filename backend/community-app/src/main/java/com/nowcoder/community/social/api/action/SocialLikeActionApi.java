package com.nowcoder.community.social.api.action;

import java.util.UUID;

public interface SocialLikeActionApi {

    LikeResult setLike(SetLikeCommand command);

    record SetLikeCommand(
            UUID actorUserId,
            int entityType,
            UUID entityId,
            Boolean liked,
            UUID entityUserId,
            UUID postId
    ) {
    }

    record LikeResult(boolean liked, long likeCount) {
    }
}
