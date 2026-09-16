package com.nowcoder.community.wallet.application;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Objects;
import java.util.UUID;

@Service
public class WalletRewardProjectionApplicationService {

    private static final String POST_PUBLISHED = "PostPublished";
    private static final String COMMENT_CREATED = "CommentCreated";
    private static final String LIKE_CREATED = "LikeCreated";
    private static final String LIKE_REMOVED = "LikeRemoved";

    private final WalletRewardApplicationService walletRewardApplicationService;

    public WalletRewardProjectionApplicationService(WalletRewardApplicationService walletRewardApplicationService) {
        this.walletRewardApplicationService = Objects.requireNonNull(walletRewardApplicationService, "walletRewardApplicationService must not be null");
    }

    public void postPublished(UUID postId, UUID userId) {
        if (postId == null || userId == null) {
            return;
        }
        applyDelta(userId, 10, "post-published:" + postId, POST_PUBLISHED);
    }

    public void commentCreated(UUID commentId, UUID userId) {
        if (commentId == null || userId == null) {
            return;
        }
        applyDelta(userId, 2, "comment-created:" + commentId, COMMENT_CREATED);
    }

    public void likeCreated(String sourceId, UUID actorUserId, UUID ownerUserId) {
        applyLikeDelta(sourceId, actorUserId, ownerUserId, 1, LIKE_CREATED);
    }

    public void likeRemoved(String sourceId, UUID actorUserId, UUID ownerUserId) {
        applyLikeDelta(sourceId, actorUserId, ownerUserId, -1, LIKE_REMOVED);
    }

    private void applyLikeDelta(String sourceId, UUID actorUserId, UUID ownerUserId, int delta, String sourceType) {
        if (!StringUtils.hasText(sourceId) || ownerUserId == null || ownerUserId.equals(actorUserId)) {
            return;
        }
        applyDelta(ownerUserId, delta, sourceId.trim(), sourceType);
    }

    private void applyDelta(UUID userId, int delta, String sourceId, String sourceType) {
        walletRewardApplicationService.applyDelta(new WalletRewardApplicationService.RewardCommand(
                "wallet-reward:" + sourceId,
                userId,
                delta,
                sourceType
        ));
    }
}
