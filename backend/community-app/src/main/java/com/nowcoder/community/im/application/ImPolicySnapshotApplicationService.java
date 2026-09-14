package com.nowcoder.community.im.application;

import com.nowcoder.community.im.common.projection.ProjectionVersions;
import com.nowcoder.community.im.common.projection.UserBlockRelationEntry;
import com.nowcoder.community.im.common.projection.UserBlockRelationSnapshot;
import com.nowcoder.community.im.common.projection.UserMessagingPolicyEntry;
import com.nowcoder.community.im.common.projection.UserMessagingPolicySnapshot;
import com.nowcoder.community.social.api.model.SocialBlockRelationView;
import com.nowcoder.community.social.api.query.SocialBlockQueryApi;
import com.nowcoder.community.user.api.model.UserModerationStateView;
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
    private final Clock clock;

    public ImPolicySnapshotApplicationService(
            UserModerationQueryApi userModerationQueryApi,
            SocialBlockQueryApi socialBlockQueryApi,
            Clock clock
    ) {
        this.userModerationQueryApi = Objects.requireNonNull(userModerationQueryApi, "userModerationQueryApi");
        this.socialBlockQueryApi = Objects.requireNonNull(socialBlockQueryApi, "socialBlockQueryApi");
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
                .map(state -> UserMessagingPolicyEntryAssembler.assemble(state, now))
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
}
