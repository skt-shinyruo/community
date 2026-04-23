package com.nowcoder.community.im.realtime.projection;

import com.nowcoder.community.im.common.projection.RoomMembershipEntry;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class MembershipProjectionService {

    private final MembershipSnapshotClient membershipSnapshotClient;
    private final AtomicReference<Map<UUID, Set<UUID>>> roomIdsByUser = new AtomicReference<>(Map.of());
    private final AtomicReference<Map<UUID, Set<UUID>>> memberIdsByRoom = new AtomicReference<>(Map.of());

    public MembershipProjectionService(MembershipSnapshotClient membershipSnapshotClient) {
        this.membershipSnapshotClient = membershipSnapshotClient;
    }

    public Mono<Void> refreshNow() {
        return membershipSnapshotClient.fetchAll()
                .collectList()
                .doOnNext(this::replaceSnapshot)
                .then();
    }

    public Set<UUID> roomIdsForUser(UUID userId) {
        return roomIdsByUser.get().getOrDefault(userId, Set.of());
    }

    public boolean isMember(UUID roomId, UUID userId) {
        return memberIdsByRoom.get().getOrDefault(roomId, Set.of()).contains(userId);
    }

    private void replaceSnapshot(List<RoomMembershipEntry> entries) {
        Map<UUID, Set<UUID>> roomsByUser = new ConcurrentHashMap<>();
        Map<UUID, Set<UUID>> usersByRoom = new ConcurrentHashMap<>();
        for (RoomMembershipEntry entry : entries) {
            if (entry == null || entry.userId() == null || entry.roomId() == null) {
                continue;
            }
            roomsByUser.computeIfAbsent(entry.userId(), ignored -> new LinkedHashSet<>()).add(entry.roomId());
            usersByRoom.computeIfAbsent(entry.roomId(), ignored -> new LinkedHashSet<>()).add(entry.userId());
        }
        this.roomIdsByUser.set(toImmutableCopy(roomsByUser));
        this.memberIdsByRoom.set(toImmutableCopy(usersByRoom));
    }

    private static Map<UUID, Set<UUID>> toImmutableCopy(Map<UUID, Set<UUID>> source) {
        Map<UUID, Set<UUID>> copy = new ConcurrentHashMap<>();
        for (Map.Entry<UUID, Set<UUID>> entry : source.entrySet()) {
            copy.put(entry.getKey(), Set.copyOf(entry.getValue()));
        }
        return Map.copyOf(copy);
    }
}
