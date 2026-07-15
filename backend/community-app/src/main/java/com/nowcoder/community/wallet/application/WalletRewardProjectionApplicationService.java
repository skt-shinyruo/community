package com.nowcoder.community.wallet.application;

import com.nowcoder.community.wallet.application.command.RewardProjectionCommand;
import com.nowcoder.community.wallet.application.command.WalletRewardCommand;
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
        this.walletRewardApplicationService = walletRewardApplicationService;
    }

    public RewardProjectionCommand commandForPostPublished(UUID postId, UUID userId) {
        if (postId == null || userId == null) {
            return null;
        }
        return new RewardProjectionCommand(userId, 10, "post-published:" + postId, POST_PUBLISHED);
    }

    public RewardProjectionCommand commandForCommentCreated(UUID commentId, UUID userId) {
        if (commentId == null || userId == null) {
            return null;
        }
        return new RewardProjectionCommand(userId, 2, "comment-created:" + commentId, COMMENT_CREATED);
    }

    public RewardProjectionCommand commandForLikeCreated(String sourceId, UUID actorUserId, UUID ownerUserId) {
        return commandForLike(sourceId, actorUserId, ownerUserId, 1, LIKE_CREATED);
    }

    public RewardProjectionCommand commandForLikeRemoved(String sourceId, UUID actorUserId, UUID ownerUserId) {
        return commandForLike(sourceId, actorUserId, ownerUserId, -1, LIKE_REMOVED);
    }

    private RewardProjectionCommand commandForLike(
            String sourceId,
            UUID actorUserId,
            UUID ownerUserId,
            int delta,
            String sourceType
    ) {
        if (!StringUtils.hasText(sourceId) || ownerUserId == null || ownerUserId.equals(actorUserId)) {
            return null;
        }
        return new RewardProjectionCommand(ownerUserId, delta, sourceId.trim(), sourceType);
    }

    public void apply(RewardProjectionCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        if (command.userId() == null
                || command.delta() == 0
                || !StringUtils.hasText(command.sourceId())
                || !StringUtils.hasText(command.sourceType())) {
            return;
        }
        walletRewardApplicationService.applyDelta(new WalletRewardCommand(
                "wallet-reward:" + command.sourceId().trim(),
                command.userId(),
                command.delta(),
                command.sourceType().trim()
        ));
    }
}
