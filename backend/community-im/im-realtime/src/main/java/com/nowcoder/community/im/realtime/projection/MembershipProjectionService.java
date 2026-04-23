package com.nowcoder.community.im.realtime.projection;

import com.nowcoder.community.im.common.projection.RoomMembershipEntry;
import com.nowcoder.community.im.common.event.RoomMemberChanged;
import com.nowcoder.community.im.realtime.presence.RoomLocalIndex;
import com.nowcoder.community.im.realtime.presence.WsConnection;
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

    public void bindExistingRooms(WsConnection conn, RoomLocalIndex roomLocalIndex) {
        if (conn == null || conn.userId() == null || roomLocalIndex == null) {
            return;
        }
        for (UUID roomId : roomIdsForUser(conn.userId())) {
            conn.joinRoom(roomId);
            roomLocalIndex.add(roomId, conn.connectionId());
        }
    }

    public synchronized void applyRoomMemberChanged(RoomMemberChanged event) {
        if (event == null || event.roomId() == null || event.userId() == null) {
            return;
        }
        String action = event.action() == null ? "" : event.action().trim().toUpperCase();
        if (!"JOINED".equals(action) && !"LEFT".equals(action)) {
            return;
        }

        Map<UUID, Set<UUID>> roomsByUser = toMutableCopy(roomIdsByUser.get());
        Map<UUID, Set<UUID>> usersByRoom = toMutableCopy(memberIdsByRoom.get());

        if ("JOINED".equals(action)) {
            roomsByUser.computeIfAbsent(event.userId(), ignored -> new LinkedHashSet<>()).add(event.roomId());
            usersByRoom.computeIfAbsent(event.roomId(), ignored -> new LinkedHashSet<>()).add(event.userId());
        } else {
            removeMembership(roomsByUser, event.userId(), event.roomId());
            removeMembership(usersByRoom, event.roomId(), event.userId());
        }

        roomIdsByUser.set(toImmutableCopy(roomsByUser));
        memberIdsByRoom.set(toImmutableCopy(usersByRoom));
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

    private static Map<UUID, Set<UUID>> toMutableCopy(Map<UUID, Set<UUID>> source) {
        Map<UUID, Set<UUID>> copy = new ConcurrentHashMap<>();
        for (Map.Entry<UUID, Set<UUID>> entry : source.entrySet()) {
            copy.put(entry.getKey(), new LinkedHashSet<>(entry.getValue()));
        }
        return copy;
    }

    private static void removeMembership(Map<UUID, Set<UUID>> index, UUID leftKey, UUID rightKey) {
        Set<UUID> values = index.get(leftKey);
        if (values == null) {
            return;
        }
        values.remove(rightKey);
        if (values.isEmpty()) {
            index.remove(leftKey);
        }
    }
}
