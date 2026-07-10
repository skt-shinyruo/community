package com.nowcoder.community.im.realtime.projection;

import com.nowcoder.community.im.common.projection.RoomMembershipEntry;
import com.nowcoder.community.im.common.event.RoomMemberChanged;
import com.nowcoder.community.im.common.projection.ProjectionVersions;
import com.nowcoder.community.im.realtime.presence.RoomLocalPresenceService;
import com.nowcoder.community.im.realtime.presence.WsConnection;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.HashSet;
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
    private final AtomicReference<Map<String, MembershipProjectionEntry>> memberships = new AtomicReference<>(Map.of());
    private final AtomicReference<Map<UUID, Set<UUID>>> roomIdsByUser = new AtomicReference<>(Map.of());
    private final AtomicReference<Map<UUID, Set<UUID>>> memberIdsByRoom = new AtomicReference<>(Map.of());

    public MembershipProjectionService(MembershipSnapshotClient membershipSnapshotClient) {
        this.membershipSnapshotClient = membershipSnapshotClient;
    }

    public Mono<Void> refreshNow() {
        return membershipSnapshotClient.fetchSnapshot()
                .doOnNext(this::replaceSnapshot)
                .then();
    }

    public Set<UUID> roomIdsForUser(UUID userId) {
        return roomIdsByUser.get().getOrDefault(userId, Set.of());
    }

    public boolean isMember(UUID roomId, UUID userId) {
        return memberIdsByRoom.get().getOrDefault(roomId, Set.of()).contains(userId);
    }

    public void bindExistingRooms(WsConnection conn, RoomLocalPresenceService roomLocalPresenceService) {
        if (conn == null || conn.userId() == null || roomLocalPresenceService == null) {
            return;
        }
        for (UUID roomId : roomIdsForUser(conn.userId())) {
            conn.joinRoom(roomId);
            roomLocalPresenceService.addLocalConnection(roomId, conn.connectionId());
        }
    }

    public synchronized boolean applyRoomMemberChanged(RoomMemberChanged event) {
        if (event == null || event.roomId() == null || event.userId() == null) {
            return false;
        }
        String action = event.action() == null ? "" : event.action().trim().toUpperCase();
        if (!"JOINED".equals(action) && !"LEFT".equals(action)) {
            return false;
        }
        String key = membershipKey(event.roomId(), event.userId());
        long version = event.version();
        MembershipProjectionEntry current = memberships.get().get(key);
        if (!isNewer(version, current == null ? null : current.version())) {
            return false;
        }

        Map<String, MembershipProjectionEntry> nextMemberships = new HashMap<>(memberships.get());
        nextMemberships.put(key, new MembershipProjectionEntry(
                event.roomId(),
                event.userId(),
                "JOINED".equals(action),
                version,
                event.occurredAtEpochMillis()
        ));
        replaceMembershipState(nextMemberships);
        return true;
    }

    private synchronized void replaceSnapshot(MembershipSnapshotClient.FetchedMembershipSnapshot snapshot) {
        if (snapshot == null) {
            return;
        }
        Map<String, MembershipProjectionEntry> nextMemberships = new HashMap<>(memberships.get());
        Set<String> seenKeys = new HashSet<>();
        List<RoomMembershipEntry> entries = snapshot.entries() == null ? List.of() : snapshot.entries();
        for (RoomMembershipEntry entry : entries) {
            if (entry == null || entry.userId() == null || entry.roomId() == null) {
                continue;
            }
            String key = membershipKey(entry.roomId(), entry.userId());
            long version = ProjectionVersions.snapshotEntryVersion(
                    entry.version(),
                    snapshot.snapshotHighWatermark()
            );
            seenKeys.add(key);
            MembershipProjectionEntry current = nextMemberships.get(key);
            if (isNewer(version, current == null ? null : current.version())) {
                nextMemberships.put(key, new MembershipProjectionEntry(
                        entry.roomId(),
                        entry.userId(),
                        true,
                        version,
                        entry.occurredAtEpochMillis()
                ));
            }
        }
        for (Map.Entry<String, MembershipProjectionEntry> current : memberships.get().entrySet()) {
            if (seenKeys.contains(current.getKey())) {
                continue;
            }
            if (snapshot.snapshotHighWatermark() > current.getValue().version()) {
                nextMemberships.put(current.getKey(), new MembershipProjectionEntry(
                        current.getValue().roomId(),
                        current.getValue().userId(),
                        false,
                        snapshot.snapshotHighWatermark(),
                        null
                ));
            }
        }
        replaceMembershipState(nextMemberships);
    }

    private void replaceMembershipState(Map<String, MembershipProjectionEntry> nextMemberships) {
        Map<UUID, Set<UUID>> roomsByUser = new ConcurrentHashMap<>();
        Map<UUID, Set<UUID>> usersByRoom = new ConcurrentHashMap<>();
        for (MembershipProjectionEntry entry : nextMemberships.values()) {
            if (entry == null || !entry.active()) {
                continue;
            }
            roomsByUser.computeIfAbsent(entry.userId(), ignored -> new LinkedHashSet<>()).add(entry.roomId());
            usersByRoom.computeIfAbsent(entry.roomId(), ignored -> new LinkedHashSet<>()).add(entry.userId());
        }
        this.memberships.set(Map.copyOf(nextMemberships));
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

    private static String membershipKey(UUID roomId, UUID userId) {
        return roomId + "->" + userId;
    }

    private static boolean isNewer(long candidateVersion, Long currentVersion) {
        long current = currentVersion == null ? Long.MIN_VALUE : currentVersion;
        return candidateVersion > current;
    }

    private record MembershipProjectionEntry(
            UUID roomId,
            UUID userId,
            boolean active,
            long version,
            Long occurredAtEpochMillis
    ) {
    }
}
