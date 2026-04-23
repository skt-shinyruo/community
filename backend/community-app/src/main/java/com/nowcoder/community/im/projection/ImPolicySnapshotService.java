package com.nowcoder.community.im.projection;

import com.nowcoder.community.im.common.projection.UserBlockRelationEntry;
import com.nowcoder.community.im.common.projection.UserBlockRelationSnapshot;
import com.nowcoder.community.im.common.projection.UserMessagingPolicyEntry;
import com.nowcoder.community.im.common.projection.UserMessagingPolicySnapshot;
import com.nowcoder.community.social.block.BlockScanRow;
import com.nowcoder.community.social.block.BlockService;
import com.nowcoder.community.user.service.UserModerationService;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class ImPolicySnapshotService {

    private final UserModerationService userModerationService;
    private final BlockService blockService;

    public ImPolicySnapshotService(UserModerationService userModerationService, BlockService blockService) {
        this.userModerationService = userModerationService;
        this.blockService = blockService;
    }

    public UserMessagingPolicySnapshot userPolicies(UUID afterUserId, int limit) {
        int normalizedLimit = normalizeLimit(limit);
        Instant now = Instant.now();
        List<UserModerationService.ModerationStatus> statuses =
                userModerationService.scanModerationStatusesAfterId(afterUserId, normalizedLimit);
        List<UserMessagingPolicyEntry> entries = statuses.stream()
                .map(status -> toUserPolicyEntry(status, now))
                .toList();

        UUID nextUserId = entries.isEmpty() ? null : entries.get(entries.size() - 1).userId();
        boolean hasMore = nextUserId != null
                && entries.size() == normalizedLimit
                && !userModerationService.scanModerationStatusesAfterId(nextUserId, 1).isEmpty();

        return new UserMessagingPolicySnapshot(entries, nextUserId, hasMore);
    }

    public UserBlockRelationSnapshot blockRelations(UUID afterBlockerUserId, UUID afterBlockedUserId, int limit) {
        int normalizedLimit = normalizeLimit(limit);
        List<BlockScanRow> rows = blockService.scanBlockRelationsAfter(afterBlockerUserId, afterBlockedUserId, normalizedLimit);
        List<UserBlockRelationEntry> entries = rows.stream()
                .map(this::toBlockRelationEntry)
                .toList();

        UUID nextBlockerUserId = entries.isEmpty() ? null : entries.get(entries.size() - 1).blockerUserId();
        UUID nextBlockedUserId = entries.isEmpty() ? null : entries.get(entries.size() - 1).blockedUserId();
        boolean hasMore = nextBlockerUserId != null
                && nextBlockedUserId != null
                && entries.size() == normalizedLimit
                && !blockService.scanBlockRelationsAfter(nextBlockerUserId, nextBlockedUserId, 1).isEmpty();

        return new UserBlockRelationSnapshot(entries, nextBlockerUserId, nextBlockedUserId, hasMore);
    }

    private UserMessagingPolicyEntry toUserPolicyEntry(UserModerationService.ModerationStatus status, Instant now) {
        boolean suspended = status != null && status.getBanUntil() != null && status.getBanUntil().isAfter(now);
        boolean muted = status != null && status.getMuteUntil() != null && status.getMuteUntil().isAfter(now);
        boolean allowPrivateMessages = status != null && status.getUserId() != null && !suspended && !muted;
        return new UserMessagingPolicyEntry(
                status == null ? null : status.getUserId(),
                status != null && status.getUserId() != null,
                suspended,
                muted,
                allowPrivateMessages
        );
    }

    private UserBlockRelationEntry toBlockRelationEntry(BlockScanRow row) {
        return new UserBlockRelationEntry(row.getUserId(), row.getTargetUserId(), true);
    }

    private int normalizeLimit(int limit) {
        return Math.min(500, Math.max(1, limit));
    }
}
