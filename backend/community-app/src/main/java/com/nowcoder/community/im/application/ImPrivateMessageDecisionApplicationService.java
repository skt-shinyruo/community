package com.nowcoder.community.im.application;

import com.nowcoder.community.im.common.policy.PrivateMessagePolicyDecision;
import com.nowcoder.community.im.common.projection.UserMessagingPolicyEntry;
import com.nowcoder.community.social.api.query.SocialBlockQueryApi;
import com.nowcoder.community.user.api.query.UserLookupQueryApi;
import com.nowcoder.community.user.api.query.UserModerationQueryApi;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Service
public class ImPrivateMessageDecisionApplicationService {

    private final UserModerationQueryApi userModerationQueryApi;
    private final SocialBlockQueryApi socialBlockQueryApi;
    private final UserLookupQueryApi userLookupQueryApi;
    private final Clock clock;

    public ImPrivateMessageDecisionApplicationService(
            UserModerationQueryApi userModerationQueryApi,
            SocialBlockQueryApi socialBlockQueryApi,
            UserLookupQueryApi userLookupQueryApi,
            Clock clock
    ) {
        this.userModerationQueryApi = Objects.requireNonNull(userModerationQueryApi, "userModerationQueryApi");
        this.socialBlockQueryApi = Objects.requireNonNull(socialBlockQueryApi, "socialBlockQueryApi");
        this.userLookupQueryApi = Objects.requireNonNull(userLookupQueryApi, "userLookupQueryApi");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public PrivateMessagePolicyDecision decidePrivateMessage(UUID fromUserId, UUID toUserId) {
        if (fromUserId == null || toUserId == null || fromUserId.equals(toUserId)) {
            return PrivateMessagePolicyDecision.deny(400, "invalid_request", "参数错误");
        }
        if (userLookupQueryApi.getSummaryById(fromUserId) == null) {
            return PrivateMessagePolicyDecision.deny(404, "policy_denied", "发送方不存在");
        }
        if (userLookupQueryApi.getSummaryById(toUserId) == null) {
            return PrivateMessagePolicyDecision.deny(404, "policy_denied", "接收方不存在");
        }

        Instant now = clock.instant();
        UserMessagingPolicyEntry fromPolicy = UserMessagingPolicyEntryAssembler.assemble(
                userModerationQueryApi.getModerationState(fromUserId),
                now
        );
        if (fromPolicy.suspended() || fromPolicy.muted() || !fromPolicy.canSendPrivate()) {
            return PrivateMessagePolicyDecision.deny(403, "policy_denied", "发送方无权限发送私信");
        }
        UserMessagingPolicyEntry toPolicy = UserMessagingPolicyEntryAssembler.assemble(
                userModerationQueryApi.getModerationState(toUserId),
                now
        );
        if (!toPolicy.canSendPrivate()) {
            return PrivateMessagePolicyDecision.deny(403, "policy_denied", "接收方不允许私信");
        }

        if (socialBlockQueryApi.isEitherBlocked(fromUserId, toUserId)) {
            return PrivateMessagePolicyDecision.deny(403, "policy_denied", "用户已拉黑");
        }
        return PrivateMessagePolicyDecision.allow();
    }
}
