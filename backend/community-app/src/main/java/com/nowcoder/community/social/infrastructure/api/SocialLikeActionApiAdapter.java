package com.nowcoder.community.social.infrastructure.api;

import com.nowcoder.community.social.api.action.SocialLikeActionApi;
import com.nowcoder.community.social.api.model.ResolvedLikeTargetView;
import com.nowcoder.community.social.api.model.SocialLikeResultView;
import com.nowcoder.community.social.application.LikeApplicationService;
import com.nowcoder.community.social.application.command.SetLikeCommand;
import com.nowcoder.community.social.application.result.LikeResult;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.UUID;

@Service
public class SocialLikeActionApiAdapter implements SocialLikeActionApi {

    private final LikeApplicationService likeApplicationService;

    public SocialLikeActionApiAdapter(LikeApplicationService likeApplicationService) {
        this.likeApplicationService = likeApplicationService;
    }

    @Override
    public SocialLikeResultView setLike(
            UUID actorUserId,
            int entityType,
            UUID entityId,
            Boolean liked,
            ResolvedLikeTargetView resolvedTarget
    ) {
        Objects.requireNonNull(resolvedTarget, "resolvedTarget must not be null");
        LikeResult result = likeApplicationService.setLike(new SetLikeCommand(
                actorUserId,
                entityType,
                entityId,
                liked,
                resolvedTarget.entityUserId(),
                resolvedTarget.postId()
        ));
        return new SocialLikeResultView(result.liked(), result.likeCount());
    }
}
