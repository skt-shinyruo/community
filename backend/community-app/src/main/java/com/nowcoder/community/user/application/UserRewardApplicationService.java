package com.nowcoder.community.user.application;

import com.nowcoder.community.wallet.api.action.WalletRewardActionApi;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.UUID;

@Service
public class UserRewardApplicationService {

    private static final String TYPE_POST_PUBLISHED = "PostPublished";
    private static final String TYPE_COMMENT_CREATED = "CommentCreated";
    private static final String TYPE_LIKE_CREATED = "LikeCreated";
    private static final String TYPE_LIKE_REMOVED = "LikeRemoved";

    private final WalletRewardActionApi walletRewardActionApi;

    public UserRewardApplicationService(WalletRewardActionApi walletRewardActionApi) {
        this.walletRewardActionApi = walletRewardActionApi;
    }

    public RewardCommand commandForPostPublished(UUID postId, UUID userId) {
        if (postId == null || userId == null) {
            return null;
        }
        return new RewardCommand(userId, 10, "post-published:" + postId, TYPE_POST_PUBLISHED);
    }

    public RewardCommand commandForCommentCreated(UUID commentId, UUID userId) {
        if (commentId == null || userId == null) {
            return null;
        }
        return new RewardCommand(userId, 2, "comment-created:" + commentId, TYPE_COMMENT_CREATED);
    }

    public RewardCommand commandForLikeCreated(String sourceEventId, UUID actorUserId, UUID entityUserId) {
        return commandForLike(sourceEventId, actorUserId, entityUserId, 1, TYPE_LIKE_CREATED);
    }

    public RewardCommand commandForLikeRemoved(String sourceEventId, UUID actorUserId, UUID entityUserId) {
        return commandForLike(sourceEventId, actorUserId, entityUserId, -1, TYPE_LIKE_REMOVED);
    }

    private RewardCommand commandForLike(
            String sourceEventId,
            UUID actorUserId,
            UUID entityUserId,
            int delta,
            String sourceEventType
    ) {
        if (!StringUtils.hasText(sourceEventId) || entityUserId == null || entityUserId.equals(actorUserId)) {
            return null;
        }
        return new RewardCommand(entityUserId, delta, sourceEventId.trim(), sourceEventType);
    }

    public void apply(RewardCommand command) {
        if (command == null
                || command.userId() == null
                || command.delta() == 0
                || !StringUtils.hasText(command.sourceEventId())
                || !StringUtils.hasText(command.sourceEventType())) {
            return;
        }
        walletRewardActionApi.applyDelta(
                "wallet-reward:" + command.sourceEventId().trim(),
                command.userId(),
                command.delta(),
                command.sourceEventType().trim()
        );
    }

    public record RewardCommand(
            UUID userId,
            int delta,
            String sourceEventId,
            String sourceEventType
    ) {
    }
}
