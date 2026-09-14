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
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class PolicyProjectionService {

    private final PolicySnapshotClient policySnapshotClient;
    private final AtomicReference<PolicyProjectionState> state;

    public PolicyProjectionService(PolicySnapshotClient policySnapshotClient) {
        this.policySnapshotClient = policySnapshotClient;
        this.state = new AtomicReference<>(PolicyProjectionState.empty());
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
        PolicyProjectionState current = state.get();
        UserMessagingPolicyEntry fromPolicy = current.policiesByUser().get(fromUserId);
        UserMessagingPolicyEntry toPolicy = current.policiesByUser().get(toUserId);
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
        if (isBlocked(current, fromUserId, toUserId) || isBlocked(current, toUserId, fromUserId)) {
            return PolicyDecision.deny(403, "policy_denied", "用户已拉黑");
        }
        return PolicyDecision.allow();
    }

    public PolicyDecision canSendPrivate(UUID fromUserId, UUID toUserId) {
        return canSendPrivateMessage(fromUserId, toUserId);
    }

    public synchronized void applyUserMessagingPolicyChanged(UserMessagingPolicyChanged event) {
        if (event == null) {
            throw new IllegalArgumentException("user messaging policy changed event must not be null");
        }
        if (event.eventId() == null || event.eventId().isBlank()) {
            throw new IllegalArgumentException("user messaging policy changed event must carry a non-blank eventId");
        }
        if (event.userId() == null) {
            throw new IllegalArgumentException("user messaging policy changed event must carry a userId identity");
        }
        long version = event.version();
        PolicyProjectionState current = state.get();
        UserMessagingPolicyEntry currentPolicy = current.policiesByUser().get(event.userId());
        if (!isNewer(version, currentVersion(currentPolicy))) {
            return;
        }
        Map<UUID, UserMessagingPolicyEntry> nextPolicies = new HashMap<>(current.policiesByUser());
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
        state.set(new PolicyProjectionState(Map.copyOf(nextPolicies), current.blockRelations()));
    }

    public synchronized boolean applyUserBlockRelationChanged(UserBlockRelationChanged event) {
        if (event == null) {
            throw new IllegalArgumentException("user block relation changed event must not be null");
        }
        if (event.eventId() == null || event.eventId().isBlank()) {
            throw new IllegalArgumentException("user block relation changed event must carry a non-blank eventId");
        }
        if (event.blockerUserId() == null || event.blockedUserId() == null) {
            throw new IllegalArgumentException(
                    "user block relation changed event must carry a complete blocker/blocked identity");
        }
        String key = blockKey(event.blockerUserId(), event.blockedUserId());
        long version = event.version();
        PolicyProjectionState current = state.get();
        BlockProjectionEntry currentBlock = current.blockRelations().get(key);
        if (!isNewer(version, currentBlock == null ? null : currentBlock.version())) {
            return false;
        }
        Map<String, BlockProjectionEntry> nextBlocks = new HashMap<>(current.blockRelations());
        nextBlocks.put(key, new BlockProjectionEntry(event.active(), version, event.occurredAtEpochMillis()));
        state.set(new PolicyProjectionState(current.policiesByUser(), Map.copyOf(nextBlocks)));
        return true;
    }

    private synchronized void replaceSnapshot(
            PolicySnapshotClient.FetchedUserPolicySnapshot policySnapshot,
            PolicySnapshotClient.FetchedBlockRelationSnapshot blockSnapshot
    ) {
        requireValidPolicySnapshotEntries(policySnapshot);
        requireValidBlockSnapshotEntries(blockSnapshot);
        PolicyProjectionState current = state.get();
        Map<UUID, UserMessagingPolicyEntry> nextPolicies = mergePolicySnapshot(
                current.policiesByUser(),
                policySnapshot
        );
        Map<String, BlockProjectionEntry> nextBlocks = mergeBlockSnapshot(
                current.blockRelations(),
                blockSnapshot
        );
        state.set(new PolicyProjectionState(nextPolicies, nextBlocks));
    }

    private static void requireValidPolicySnapshotEntries(PolicySnapshotClient.FetchedUserPolicySnapshot snapshot) {
        if (snapshot == null || snapshot.entries() == null) {
            throw new IllegalStateException("user policy snapshot omitted the entries list");
        }
        for (UserMessagingPolicyEntry entry : snapshot.entries()) {
            if (entry == null || entry.userId() == null) {
                throw new IllegalStateException(
                        "user policy snapshot contained an entry without a userId identity");
            }
        }
    }

    private static void requireValidBlockSnapshotEntries(PolicySnapshotClient.FetchedBlockRelationSnapshot snapshot) {
        if (snapshot == null || snapshot.entries() == null) {
            throw new IllegalStateException("block relation snapshot omitted the entries list");
        }
        for (UserBlockRelationEntry entry : snapshot.entries()) {
            if (entry == null || entry.blockerUserId() == null || entry.blockedUserId() == null) {
                throw new IllegalStateException(
                        "block relation snapshot contained an entry without a complete blocker/blocked identity");
            }
        }
    }

    private static Map<UUID, UserMessagingPolicyEntry> mergePolicySnapshot(
            Map<UUID, UserMessagingPolicyEntry> currentPolicies,
            PolicySnapshotClient.FetchedUserPolicySnapshot snapshot
    ) {
        Map<UUID, UserMessagingPolicyEntry> nextPolicies = new HashMap<>(currentPolicies);
        Set<UUID> seenUserIds = new HashSet<>();
        for (UserMessagingPolicyEntry entry : snapshot.entries()) {
            long version = ProjectionVersions.snapshotEntryVersion(
                    entry.version(),
                    snapshot.snapshotHighWatermark()
            );
            seenUserIds.add(entry.userId());
            UserMessagingPolicyEntry current = nextPolicies.get(entry.userId());
            if (isNewer(version, currentVersion(current))) {
                nextPolicies.put(entry.userId(), withVersion(entry, version));
            }
        }
        for (Map.Entry<UUID, UserMessagingPolicyEntry> current : currentPolicies.entrySet()) {
            if (seenUserIds.contains(current.getKey())) {
                continue;
            }
            long currentVersion = currentVersion(current.getValue());
            if (snapshot.snapshotHighWatermark() > currentVersion) {
                nextPolicies.remove(current.getKey());
            }
        }
        return Map.copyOf(nextPolicies);
    }

    private static Map<String, BlockProjectionEntry> mergeBlockSnapshot(
            Map<String, BlockProjectionEntry> currentBlocks,
            PolicySnapshotClient.FetchedBlockRelationSnapshot snapshot
    ) {
        Map<String, BlockProjectionEntry> nextBlocks = new HashMap<>(currentBlocks);
        Set<String> seenKeys = new HashSet<>();
        for (UserBlockRelationEntry entry : snapshot.entries()) {
            String key = blockKey(entry.blockerUserId(), entry.blockedUserId());
            long version = ProjectionVersions.snapshotEntryVersion(
                    entry.version(),
                    snapshot.snapshotHighWatermark()
            );
            seenKeys.add(key);
            BlockProjectionEntry current = nextBlocks.get(key);
            if (isNewer(version, current == null ? null : current.version())) {
                nextBlocks.put(key, new BlockProjectionEntry(entry.active(), version, entry.occurredAtEpochMillis()));
            }
        }
        for (Map.Entry<String, BlockProjectionEntry> current : currentBlocks.entrySet()) {
            if (seenKeys.contains(current.getKey())) {
                continue;
            }
            if (snapshot.snapshotHighWatermark() > current.getValue().version()) {
                nextBlocks.put(current.getKey(), new BlockProjectionEntry(false, snapshot.snapshotHighWatermark(), null));
            }
        }
        return Map.copyOf(nextBlocks);
    }

    private static boolean isBlocked(PolicyProjectionState current, UUID blockerUserId, UUID blockedUserId) {
        BlockProjectionEntry entry = current.blockRelations().get(blockKey(blockerUserId, blockedUserId));
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
        return entry.version();
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

    private record PolicyProjectionState(
            Map<UUID, UserMessagingPolicyEntry> policiesByUser,
            Map<String, BlockProjectionEntry> blockRelations
    ) {

        private static PolicyProjectionState empty() {
            return new PolicyProjectionState(Map.of(), Map.of());
        }
    }

    private record BlockProjectionEntry(boolean active, long version, Long occurredAtEpochMillis) {
    }
}
