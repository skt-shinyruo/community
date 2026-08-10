package com.nowcoder.community.im.application;

import com.nowcoder.community.im.common.projection.ProjectionVersions;
import com.nowcoder.community.im.common.projection.UserBlockRelationEntry;
import com.nowcoder.community.im.common.projection.UserBlockRelationSnapshot;
import com.nowcoder.community.im.common.projection.UserMessagingPolicyEntry;
import com.nowcoder.community.im.common.projection.UserMessagingPolicySnapshot;
import com.nowcoder.community.im.common.policy.PrivateMessagePolicyDecision;
import com.nowcoder.community.social.api.model.SocialBlockRelationView;
import com.nowcoder.community.social.api.query.SocialBlockQueryApi;
import com.nowcoder.community.user.api.model.UserModerationStateView;
import com.nowcoder.community.user.api.query.UserLookupQueryApi;
import com.nowcoder.community.user.api.query.UserModerationQueryApi;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class ImPolicySnapshotApplicationService {

    private final UserModerationQueryApi userModerationQueryApi;
    private final SocialBlockQueryApi socialBlockQueryApi;
    private final UserLookupQueryApi userLookupQueryApi;
    private final Clock clock;

    public ImPolicySnapshotApplicationService(
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

    public UserMessagingPolicySnapshot userPolicies(UUID afterUserId, int limit) {
        return userPolicies(afterUserId, limit, null);
    }

    public UserMessagingPolicySnapshot userPolicies(UUID afterUserId, int limit, Long snapshotVersion) {
        requireSnapshotVersionForContinuation(afterUserId != null, snapshotVersion);
        int normalizedLimit = normalizeLimit(limit);
        Instant now = clock.instant();
        long currentVersion = userModerationQueryApi.currentModerationProjectionVersion();
        long snapshotHighWatermark = resolveSnapshotVersion(snapshotVersion, currentVersion);
        List<UserModerationStateView> states = userModerationQueryApi.scanModerationStatesAtVersionAfterId(
                snapshotHighWatermark,
                afterUserId,
                normalizedLimit
        );
        List<UserMessagingPolicyEntry> entries = states.stream()
                .map(state -> toUserPolicyEntry(state, now))
                .toList();

        UUID nextUserId = entries.isEmpty() ? null : entries.get(entries.size() - 1).userId();
        boolean hasMore = nextUserId != null
                && entries.size() == normalizedLimit
                && !userModerationQueryApi.scanModerationStatesAtVersionAfterId(
                        snapshotHighWatermark,
                        nextUserId,
                        1
                ).isEmpty();

        return new UserMessagingPolicySnapshot(entries, nextUserId, hasMore, snapshotHighWatermark);
    }

    public UserBlockRelationSnapshot blockRelations(UUID afterBlockerUserId, UUID afterBlockedUserId, int limit) {
        return blockRelations(afterBlockerUserId, afterBlockedUserId, limit, null);
    }

    public UserBlockRelationSnapshot blockRelations(
            UUID afterBlockerUserId,
            UUID afterBlockedUserId,
            int limit,
            Long snapshotVersion
    ) {
        boolean continuation = afterBlockerUserId != null || afterBlockedUserId != null;
        requireSnapshotVersionForContinuation(continuation, snapshotVersion);
        int normalizedLimit = normalizeLimit(limit);
        long occurredAtEpochMillis = clock.millis();
        long currentVersion = socialBlockQueryApi.currentBlockProjectionVersion();
        long snapshotHighWatermark = resolveSnapshotVersion(snapshotVersion, currentVersion);
        List<SocialBlockRelationView> views =
                socialBlockQueryApi.scanBlockRelationsAtVersionAfter(
                        snapshotHighWatermark,
                        afterBlockerUserId,
                        afterBlockedUserId,
                        normalizedLimit
                );
        List<UserBlockRelationEntry> entries = views.stream()
                .map(view -> toBlockRelationEntry(view, occurredAtEpochMillis))
                .toList();

        UUID nextBlockerUserId = entries.isEmpty() ? null : entries.get(entries.size() - 1).blockerUserId();
        UUID nextBlockedUserId = entries.isEmpty() ? null : entries.get(entries.size() - 1).blockedUserId();
        boolean hasMore = nextBlockerUserId != null
                && nextBlockedUserId != null
                && entries.size() == normalizedLimit
                && !socialBlockQueryApi.scanBlockRelationsAtVersionAfter(
                        snapshotHighWatermark,
                        nextBlockerUserId,
                        nextBlockedUserId,
                        1
                ).isEmpty();

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

        Instant now = clock.instant();
        UserMessagingPolicyEntry fromPolicy = toUserPolicyEntry(
                userModerationQueryApi.getModerationState(fromUserId),
                now
        );
        if (fromPolicy.suspended() || fromPolicy.muted() || !fromPolicy.canSendPrivate()) {
            return PrivateMessagePolicyDecision.deny(403, "policy_denied", "发送方无权限发送私信");
        }
        UserMessagingPolicyEntry toPolicy = toUserPolicyEntry(
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

    private UserMessagingPolicyEntry toUserPolicyEntry(UserModerationStateView state, Instant now) {
        if (state == null) {
            throw new IllegalStateException("user moderation state must not be null");
        }
        if (state.userId() == null) {
            throw new IllegalStateException("user moderation state userId must not be null");
        }
        long version = ProjectionVersions.requirePositive(state.version(), "version");
        boolean suspended = state.banUntil() != null && state.banUntil().isAfter(now);
        boolean muted = state.muteUntil() != null && state.muteUntil().isAfter(now);
        boolean canSendPrivate = !suspended && !muted;
        return new UserMessagingPolicyEntry(
                state.userId(),
                true,
                suspended,
                muted,
                toEpochMillis(state.muteUntil()),
                toEpochMillis(state.banUntil()),
                canSendPrivate,
                version,
                now.toEpochMilli()
        );
    }

    private UserBlockRelationEntry toBlockRelationEntry(
            SocialBlockRelationView view,
            long occurredAtEpochMillis
    ) {
        if (view == null) {
            throw new IllegalStateException("social block snapshot relation must not be null");
        }
        if (view.blockerUserId() == null) {
            throw new IllegalStateException("social block relation blockerUserId must not be null");
        }
        if (view.blockedUserId() == null) {
            throw new IllegalStateException("social block relation blockedUserId must not be null");
        }
        long version = ProjectionVersions.requirePositive(view.version(), "version");
        return new UserBlockRelationEntry(
                view.blockerUserId(),
                view.blockedUserId(),
                true,
                version,
                occurredAtEpochMillis
        );
    }

    private int normalizeLimit(int limit) {
        return Math.min(500, Math.max(1, limit));
    }

    private long resolveSnapshotVersion(Long requestedVersion, long currentVersion) {
        if (currentVersion < 0L) {
            throw new IllegalStateException("owner projection version must be non-negative");
        }
        if (requestedVersion == null) {
            return currentVersion;
        }
        if (requestedVersion < 0L) {
            throw new IllegalArgumentException("snapshotVersion must be non-negative");
        }
        if (requestedVersion > currentVersion) {
            throw new IllegalArgumentException("snapshotVersion must not exceed current owner version");
        }
        return requestedVersion;
    }

    private void requireSnapshotVersionForContinuation(boolean continuation, Long snapshotVersion) {
        if (continuation && snapshotVersion == null) {
            throw new IllegalArgumentException("snapshotVersion is required for a continuation page");
        }
    }

    private Long toEpochMillis(Instant instant) {
        return instant == null ? null : instant.toEpochMilli();
    }
}
