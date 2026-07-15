package com.nowcoder.community.social.api.action;

import com.nowcoder.community.social.api.model.ResolvedLikeTargetView;
import com.nowcoder.community.social.api.model.SocialLikeResultView;

import java.util.UUID;

public interface SocialLikeActionApi {

    SocialLikeResultView setLike(
            UUID actorUserId,
            int entityType,
            UUID entityId,
            Boolean liked,
            ResolvedLikeTargetView resolvedTarget
    );
}
