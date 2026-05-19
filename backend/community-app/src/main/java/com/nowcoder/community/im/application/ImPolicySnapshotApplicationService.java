package com.nowcoder.community.im.application;

import com.nowcoder.community.im.common.projection.UserBlockRelationEntry;
import com.nowcoder.community.im.common.projection.UserBlockRelationSnapshot;
import com.nowcoder.community.im.common.projection.UserMessagingPolicyEntry;
import com.nowcoder.community.im.common.projection.UserMessagingPolicySnapshot;
import com.nowcoder.community.im.common.projection.ProjectionVersions;
import com.nowcoder.community.im.common.policy.PrivateMessagePolicyDecision;
import com.nowcoder.community.social.api.model.SocialBlockRelationView;
import com.nowcoder.community.social.api.query.SocialBlockQueryApi;
import com.nowcoder.community.user.api.model.UserModerationStateView;
import com.nowcoder.community.user.api.query.UserLookupQueryApi;
import com.nowcoder.community.user.api.query.UserModerationQueryApi;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class ImPolicySnapshotApplicationService {

    private final UserModerationQueryApi userModerationQueryApi;
    private final SocialBlockQueryApi socialBlockQueryApi;
    private final UserLookupQueryApi userLookupQueryApi;

    public ImPolicySnapshotApplicationService(
            UserModerationQueryApi userModerationQueryApi,
            SocialBlockQueryApi socialBlockQueryApi,
            UserLookupQueryApi userLookupQueryApi
    ) {
        this.userModerationQueryApi = userModerationQueryApi;
        this.socialBlockQueryApi = socialBlockQueryApi;
        this.userLookupQueryApi = userLookupQueryApi;
    }

    public UserMessagingPolicySnapshot userPolicies(UUID afterUserId, int limit) {
        int normalizedLimit = normalizeLimit(limit);
        Instant now = Instant.now();
        long snapshotHighWatermark = ProjectionVersions.snapshotHighWatermarkFromEpochMillis(now.toEpochMilli());
        List<UserModerationStateView> states = userModerationQueryApi.scanModerationStatesAfterId(afterUserId, normalizedLimit);
        List<UserMessagingPolicyEntry> entries = states.stream()
                .map(state -> toUserPolicyEntry(state, now, snapshotHighWatermark))
                .toList();

        UUID nextUserId = entries.isEmpty() ? null : entries.get(entries.size() - 1).userId();
        boolean hasMore = nextUserId != null
                && entries.size() == normalizedLimit
                && !userModerationQueryApi.scanModerationStatesAfterId(nextUserId, 1).isEmpty();

        return new UserMessagingPolicySnapshot(entries, nextUserId, hasMore, snapshotHighWatermark);
    }

    public UserBlockRelationSnapshot blockRelations(UUID afterBlockerUserId, UUID afterBlockedUserId, int limit) {
        int normalizedLimit = normalizeLimit(limit);
        long occurredAtEpochMillis = System.currentTimeMillis();
        long snapshotHighWatermark = ProjectionVersions.snapshotHighWatermarkFromEpochMillis(occurredAtEpochMillis);
        List<SocialBlockRelationView> views =
                socialBlockQueryApi.scanBlockRelationsAfter(afterBlockerUserId, afterBlockedUserId, normalizedLimit);
        List<UserBlockRelationEntry> entries = views.stream()
                .map(view -> toBlockRelationEntry(view, snapshotHighWatermark, occurredAtEpochMillis))
                .toList();

        UUID nextBlockerUserId = entries.isEmpty() ? null : entries.get(entries.size() - 1).blockerUserId();
        UUID nextBlockedUserId = entries.isEmpty() ? null : entries.get(entries.size() - 1).blockedUserId();
        boolean hasMore = nextBlockerUserId != null
                && nextBlockedUserId != null
                && entries.size() == normalizedLimit
                && !socialBlockQueryApi.scanBlockRelationsAfter(nextBlockerUserId, nextBlockedUserId, 1).isEmpty();

        return new UserBlockRelationSnapshot(entries, nextBlockerUserId, nextBlockedUserId, hasMore, snapshotHighWatermark);
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

        Instant now = Instant.now();
        UserMessagingPolicyEntry fromPolicy = toUserPolicyEntry(
                userModerationQueryApi.getModerationState(fromUserId),
                now,
                ProjectionVersions.snapshotHighWatermarkFromEpochMillis(now.toEpochMilli())
        );
        if (fromPolicy.suspended() || fromPolicy.muted() || !fromPolicy.canSendPrivate()) {
            return PrivateMessagePolicyDecision.deny(403, "policy_denied", "发送方无权限发送私信");
        }
        UserMessagingPolicyEntry toPolicy = toUserPolicyEntry(
                userModerationQueryApi.getModerationState(toUserId),
                now,
                ProjectionVersions.snapshotHighWatermarkFromEpochMillis(now.toEpochMilli())
        );
        if (!toPolicy.canSendPrivate()) {
            return PrivateMessagePolicyDecision.deny(403, "policy_denied", "接收方不允许私信");
        }

        if (socialBlockQueryApi.isEitherBlocked(fromUserId, toUserId)) {
            return PrivateMessagePolicyDecision.deny(403, "policy_denied", "用户已拉黑");
        }
        return PrivateMessagePolicyDecision.allow();
    }

    private UserMessagingPolicyEntry toUserPolicyEntry(UserModerationStateView state, Instant now, long snapshotHighWatermark) {
        boolean suspended = state != null && state.banUntil() != null && state.banUntil().isAfter(now);
        boolean muted = state != null && state.muteUntil() != null && state.muteUntil().isAfter(now);
        boolean canSendPrivate = state != null && state.userId() != null && !suspended && !muted;
        return new UserMessagingPolicyEntry(
                state == null ? null : state.userId(),
                state != null && state.userId() != null,
                suspended,
                muted,
                toEpochMillis(state == null ? null : state.muteUntil()),
                toEpochMillis(state == null ? null : state.banUntil()),
                canSendPrivate,
                snapshotHighWatermark,
                now.toEpochMilli()
        );
    }

    private UserBlockRelationEntry toBlockRelationEntry(
            SocialBlockRelationView view,
            long snapshotHighWatermark,
            long occurredAtEpochMillis
    ) {
        return new UserBlockRelationEntry(
                view.blockerUserId(),
                view.blockedUserId(),
                true,
                snapshotHighWatermark,
                occurredAtEpochMillis
        );
    }

    private int normalizeLimit(int limit) {
        return Math.min(500, Math.max(1, limit));
    }

    private Long toEpochMillis(Instant instant) {
        return instant == null ? null : instant.toEpochMilli();
    }
}
