package com.nowcoder.community.im.application;

import com.nowcoder.community.im.common.projection.UserBlockRelationEntry;
import com.nowcoder.community.im.common.projection.UserBlockRelationSnapshot;
import com.nowcoder.community.im.common.projection.UserMessagingPolicyEntry;
import com.nowcoder.community.im.common.projection.UserMessagingPolicySnapshot;
import com.nowcoder.community.social.api.model.SocialBlockRelationView;
import com.nowcoder.community.social.api.query.SocialBlockQueryApi;
import com.nowcoder.community.user.api.model.UserModerationStateView;
import com.nowcoder.community.user.api.query.UserModerationQueryApi;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class ImPolicySnapshotApplicationService {

    private final UserModerationQueryApi userModerationQueryApi;
    private final SocialBlockQueryApi socialBlockQueryApi;

    public ImPolicySnapshotApplicationService(
            UserModerationQueryApi userModerationQueryApi,
            SocialBlockQueryApi socialBlockQueryApi
    ) {
        this.userModerationQueryApi = userModerationQueryApi;
        this.socialBlockQueryApi = socialBlockQueryApi;
    }

    public UserMessagingPolicySnapshot userPolicies(UUID afterUserId, int limit) {
        int normalizedLimit = normalizeLimit(limit);
        Instant now = Instant.now();
        List<UserModerationStateView> states = userModerationQueryApi.scanModerationStatesAfterId(afterUserId, normalizedLimit);
        List<UserMessagingPolicyEntry> entries = states.stream()
                .map(state -> toUserPolicyEntry(state, now))
                .toList();

        UUID nextUserId = entries.isEmpty() ? null : entries.get(entries.size() - 1).userId();
        boolean hasMore = nextUserId != null
                && entries.size() == normalizedLimit
                && !userModerationQueryApi.scanModerationStatesAfterId(nextUserId, 1).isEmpty();

        return new UserMessagingPolicySnapshot(entries, nextUserId, hasMore);
    }

    public UserBlockRelationSnapshot blockRelations(UUID afterBlockerUserId, UUID afterBlockedUserId, int limit) {
        int normalizedLimit = normalizeLimit(limit);
        List<SocialBlockRelationView> views =
                socialBlockQueryApi.scanBlockRelationsAfter(afterBlockerUserId, afterBlockedUserId, normalizedLimit);
        List<UserBlockRelationEntry> entries = views.stream()
                .map(this::toBlockRelationEntry)
                .toList();

        UUID nextBlockerUserId = entries.isEmpty() ? null : entries.get(entries.size() - 1).blockerUserId();
        UUID nextBlockedUserId = entries.isEmpty() ? null : entries.get(entries.size() - 1).blockedUserId();
        boolean hasMore = nextBlockerUserId != null
                && nextBlockedUserId != null
                && entries.size() == normalizedLimit
                && !socialBlockQueryApi.scanBlockRelationsAfter(nextBlockerUserId, nextBlockedUserId, 1).isEmpty();

        return new UserBlockRelationSnapshot(entries, nextBlockerUserId, nextBlockedUserId, hasMore);
    }

    private UserMessagingPolicyEntry toUserPolicyEntry(UserModerationStateView state, Instant now) {
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
                canSendPrivate
        );
    }

    private UserBlockRelationEntry toBlockRelationEntry(SocialBlockRelationView view) {
        return new UserBlockRelationEntry(view.blockerUserId(), view.blockedUserId(), true);
    }

    private int normalizeLimit(int limit) {
        return Math.min(500, Math.max(1, limit));
    }

    private Long toEpochMillis(Instant instant) {
        return instant == null ? null : instant.toEpochMilli();
    }
}
