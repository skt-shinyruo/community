package com.nowcoder.community.im.realtime.projection;

import com.nowcoder.community.im.common.event.UserBlockRelationChanged;
import com.nowcoder.community.im.common.event.UserMessagingPolicyChanged;
import com.nowcoder.community.im.common.projection.ProjectionVersions;
import com.nowcoder.community.im.common.projection.UserBlockRelationEntry;
import com.nowcoder.community.im.common.projection.UserMessagingPolicyEntry;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class PolicyProjectionService {

    private final PolicySnapshotClient policySnapshotClient;
    private final AtomicReference<Map<UUID, UserMessagingPolicyEntry>> policiesByUser = new AtomicReference<>(Map.of());
    private final AtomicReference<Map<String, BlockProjectionEntry>> blockRelations = new AtomicReference<>(Map.of());

    public PolicyProjectionService(PolicySnapshotClient policySnapshotClient) {
        this.policySnapshotClient = policySnapshotClient;
    }

    public Mono<Void> refreshNow() {
        return Mono.zip(
                        policySnapshotClient.fetchUserPolicySnapshot(),
                        policySnapshotClient.fetchBlockRelationSnapshot()
                )
                .doOnNext(tuple -> replaceSnapshot(tuple.getT1(), tuple.getT2()))
                .then();
    }

    public PolicyDecision canSendPrivateMessage(UUID fromUserId, UUID toUserId) {
        if (fromUserId == null || toUserId == null) {
            return PolicyDecision.deny(400, "invalid_request", "参数错误");
        }
        UserMessagingPolicyEntry fromPolicy = policiesByUser.get().get(fromUserId);
        UserMessagingPolicyEntry toPolicy = policiesByUser.get().get(toUserId);
        if (fromPolicy == null || !fromPolicy.userExists()) {
            return PolicyDecision.deny(404, "policy_denied", "发送方不存在");
        }
        if (toPolicy == null || !toPolicy.userExists()) {
            return PolicyDecision.deny(404, "policy_denied", "接收方不存在");
        }
        if (fromPolicy.suspended() || fromPolicy.muted()) {
            return PolicyDecision.deny(403, "policy_denied", "发送方无权限发送私信");
        }
        if (!toPolicy.canSendPrivate()) {
            return PolicyDecision.deny(403, "policy_denied", "接收方不允许私信");
        }
        if (isBlocked(fromUserId, toUserId) || isBlocked(toUserId, fromUserId)) {
            return PolicyDecision.deny(403, "policy_denied", "用户已拉黑");
        }
        return PolicyDecision.allow();
    }

    public PolicyDecision canSendPrivate(UUID fromUserId, UUID toUserId) {
        return canSendPrivateMessage(fromUserId, toUserId);
    }

    public synchronized void applyUserMessagingPolicyChanged(UserMessagingPolicyChanged event) {
        if (event == null || event.userId() == null) {
            return;
        }
        long version = ProjectionVersions.resolve(event.version(), event.occurredAtEpochMillis(), null);
        UserMessagingPolicyEntry current = policiesByUser.get().get(event.userId());
        if (!isNewer(version, currentVersion(current))) {
            return;
        }
        Map<UUID, UserMessagingPolicyEntry> nextPolicies = new HashMap<>(policiesByUser.get());
        nextPolicies.put(event.userId(), new UserMessagingPolicyEntry(
                event.userId(),
                event.userExists(),
                event.suspended(),
                event.muted(),
                event.muteUntil(),
                event.banUntil(),
                event.canSendPrivate(),
                version,
                event.occurredAtEpochMillis()
        ));
        policiesByUser.set(Map.copyOf(nextPolicies));
    }

    public synchronized boolean applyUserBlockRelationChanged(UserBlockRelationChanged event) {
        if (event == null || event.blockerUserId() == null || event.blockedUserId() == null) {
            return false;
        }
        String key = blockKey(event.blockerUserId(), event.blockedUserId());
        long version = ProjectionVersions.resolve(event.version(), event.occurredAtEpochMillis(), null);
        BlockProjectionEntry current = blockRelations.get().get(key);
        if (!isNewer(version, current == null ? null : current.version())) {
            return false;
        }
        Map<String, BlockProjectionEntry> nextBlocks = new HashMap<>(blockRelations.get());
        nextBlocks.put(key, new BlockProjectionEntry(event.active(), version, event.occurredAtEpochMillis()));
        blockRelations.set(Map.copyOf(nextBlocks));
        return true;
    }

    private synchronized void replaceSnapshot(
            PolicySnapshotClient.FetchedUserPolicySnapshot policySnapshot,
            PolicySnapshotClient.FetchedBlockRelationSnapshot blockSnapshot
    ) {
        applyPolicySnapshot(policySnapshot);
        applyBlockSnapshot(blockSnapshot);
    }

    private void applyPolicySnapshot(PolicySnapshotClient.FetchedUserPolicySnapshot snapshot) {
        if (snapshot == null) {
            return;
        }
        Map<UUID, UserMessagingPolicyEntry> nextPolicies = new HashMap<>(policiesByUser.get());
        Set<UUID> seenUserIds = new HashSet<>();
        List<UserMessagingPolicyEntry> policies = snapshot.entries() == null ? List.of() : snapshot.entries();
        for (UserMessagingPolicyEntry entry : policies) {
            if (entry == null || entry.userId() == null) {
                continue;
            }
            long version = ProjectionVersions.resolve(
                    entry.version(),
                    entry.occurredAtEpochMillis(),
                    snapshot.snapshotHighWatermark()
            );
            seenUserIds.add(entry.userId());
            UserMessagingPolicyEntry current = nextPolicies.get(entry.userId());
            if (isNewer(version, currentVersion(current))) {
                nextPolicies.put(entry.userId(), withVersion(entry, version));
            }
        }
        for (Map.Entry<UUID, UserMessagingPolicyEntry> current : policiesByUser.get().entrySet()) {
            if (seenUserIds.contains(current.getKey())) {
                continue;
            }
            long currentVersion = currentVersion(current.getValue());
            if (snapshot.snapshotHighWatermark() > currentVersion) {
                nextPolicies.remove(current.getKey());
            }
        }
        policiesByUser.set(Map.copyOf(nextPolicies));
    }

    private void applyBlockSnapshot(PolicySnapshotClient.FetchedBlockRelationSnapshot snapshot) {
        if (snapshot == null) {
            return;
        }
        Map<String, BlockProjectionEntry> nextBlocks = new HashMap<>(blockRelations.get());
        Set<String> seenKeys = new HashSet<>();
        List<UserBlockRelationEntry> entries = snapshot.entries() == null ? List.of() : snapshot.entries();
        for (UserBlockRelationEntry entry : entries) {
            if (entry == null || entry.blockerUserId() == null || entry.blockedUserId() == null) {
                continue;
            }
            String key = blockKey(entry.blockerUserId(), entry.blockedUserId());
            long version = ProjectionVersions.resolve(
                    entry.version(),
                    entry.occurredAtEpochMillis(),
                    snapshot.snapshotHighWatermark()
            );
            seenKeys.add(key);
            BlockProjectionEntry current = nextBlocks.get(key);
            if (isNewer(version, current == null ? null : current.version())) {
                nextBlocks.put(key, new BlockProjectionEntry(entry.active(), version, entry.occurredAtEpochMillis()));
            }
        }
        for (Map.Entry<String, BlockProjectionEntry> current : blockRelations.get().entrySet()) {
            if (seenKeys.contains(current.getKey())) {
                continue;
            }
            if (snapshot.snapshotHighWatermark() > current.getValue().version()) {
                nextBlocks.put(current.getKey(), new BlockProjectionEntry(false, snapshot.snapshotHighWatermark(), null));
            }
        }
        blockRelations.set(Map.copyOf(nextBlocks));
    }

    private boolean isBlocked(UUID blockerUserId, UUID blockedUserId) {
        BlockProjectionEntry entry = blockRelations.get().get(blockKey(blockerUserId, blockedUserId));
        return entry != null && entry.active();
    }

    private static String blockKey(UUID blockerUserId, UUID blockedUserId) {
        return blockerUserId + "->" + blockedUserId;
    }

    private static boolean isNewer(long candidateVersion, Long currentVersion) {
        long current = currentVersion == null ? Long.MIN_VALUE : currentVersion;
        return candidateVersion > current;
    }

    private static long currentVersion(UserMessagingPolicyEntry entry) {
        if (entry == null) {
            return Long.MIN_VALUE;
        }
        return ProjectionVersions.resolve(entry.version(), entry.occurredAtEpochMillis(), null);
    }

    private static UserMessagingPolicyEntry withVersion(UserMessagingPolicyEntry entry, long version) {
        return new UserMessagingPolicyEntry(
                entry.userId(),
                entry.userExists(),
                entry.suspended(),
                entry.muted(),
                entry.muteUntil(),
                entry.banUntil(),
                entry.canSendPrivate(),
                version,
                entry.occurredAtEpochMillis()
        );
    }

    private record BlockProjectionEntry(boolean active, long version, Long occurredAtEpochMillis) {
    }
}
