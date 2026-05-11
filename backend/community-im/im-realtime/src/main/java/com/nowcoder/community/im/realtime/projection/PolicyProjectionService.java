package com.nowcoder.community.im.realtime.projection;

import com.nowcoder.community.im.common.event.UserBlockRelationChanged;
import com.nowcoder.community.im.common.event.UserMessagingPolicyChanged;
import com.nowcoder.community.im.common.projection.UserBlockRelationEntry;
import com.nowcoder.community.im.common.projection.UserMessagingPolicyEntry;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class PolicyProjectionService {

    private final PolicySnapshotClient policySnapshotClient;
    private final AtomicReference<Map<UUID, UserMessagingPolicyEntry>> policiesByUser = new AtomicReference<>(Map.of());
    private final AtomicReference<Map<String, Boolean>> activeBlocks = new AtomicReference<>(Map.of());

    public PolicyProjectionService(PolicySnapshotClient policySnapshotClient) {
        this.policySnapshotClient = policySnapshotClient;
    }

    public Mono<Void> refreshNow() {
        return Mono.zip(
                        policySnapshotClient.fetchUserPolicies().collectList(),
                        policySnapshotClient.fetchBlockRelations().collectList()
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
        Map<UUID, UserMessagingPolicyEntry> nextPolicies = new HashMap<>(policiesByUser.get());
        nextPolicies.put(event.userId(), new UserMessagingPolicyEntry(
                event.userId(),
                event.userExists(),
                event.suspended(),
                event.muted(),
                event.muteUntil(),
                event.banUntil(),
                event.canSendPrivate()
        ));
        policiesByUser.set(Map.copyOf(nextPolicies));
    }

    public synchronized void applyUserBlockRelationChanged(UserBlockRelationChanged event) {
        if (event == null || event.blockerUserId() == null || event.blockedUserId() == null) {
            return;
        }
        Map<String, Boolean> nextBlocks = new HashMap<>(activeBlocks.get());
        String key = blockKey(event.blockerUserId(), event.blockedUserId());
        if (event.active()) {
            nextBlocks.put(key, Boolean.TRUE);
        } else {
            nextBlocks.remove(key);
        }
        activeBlocks.set(Map.copyOf(nextBlocks));
    }

    private void replaceSnapshot(List<UserMessagingPolicyEntry> policies, List<UserBlockRelationEntry> blockRelations) {
        Map<UUID, UserMessagingPolicyEntry> nextPolicies = new HashMap<>();
        for (UserMessagingPolicyEntry entry : policies) {
            if (entry == null || entry.userId() == null) {
                continue;
            }
            nextPolicies.put(entry.userId(), entry);
        }

        Map<String, Boolean> nextBlocks = new HashMap<>();
        for (UserBlockRelationEntry entry : blockRelations) {
            if (entry == null || entry.blockerUserId() == null || entry.blockedUserId() == null || !entry.active()) {
                continue;
            }
            nextBlocks.put(blockKey(entry.blockerUserId(), entry.blockedUserId()), Boolean.TRUE);
        }

        this.policiesByUser.set(Map.copyOf(nextPolicies));
        this.activeBlocks.set(Map.copyOf(nextBlocks));
    }

    private boolean isBlocked(UUID blockerUserId, UUID blockedUserId) {
        return activeBlocks.get().containsKey(blockKey(blockerUserId, blockedUserId));
    }

    private static String blockKey(UUID blockerUserId, UUID blockedUserId) {
        return blockerUserId + "->" + blockedUserId;
    }
}
