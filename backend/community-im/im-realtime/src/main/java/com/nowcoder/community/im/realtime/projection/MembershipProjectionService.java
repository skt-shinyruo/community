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
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class MembershipProjectionService {

    private final MembershipSnapshotClient membershipSnapshotClient;
    private final AtomicReference<MembershipProjectionState> state;

    public MembershipProjectionService(MembershipSnapshotClient membershipSnapshotClient) {
        this.membershipSnapshotClient = membershipSnapshotClient;
        this.state = new AtomicReference<>(MembershipProjectionState.empty());
    }

    public Mono<Void> refreshNow() {
        return membershipSnapshotClient.fetchSnapshot()
                .doOnNext(this::replaceSnapshot)
                .then();
    }

    public Set<UUID> roomIdsForUser(UUID userId) {
        return state.get().roomIdsByUser().getOrDefault(userId, Set.of());
    }

    public boolean isMember(UUID roomId, UUID userId) {
        return state.get().memberIdsByRoom().getOrDefault(roomId, Set.of()).contains(userId);
    }

    public void bindExistingRooms(WsConnection conn, RoomLocalPresenceService roomLocalPresenceService) {
        if (conn == null || conn.userId() == null || roomLocalPresenceService == null) {
            return;
        }
        for (UUID roomId : roomIdsForUser(conn.userId())) {
            roomLocalPresenceService.joinLocalRoom(roomId, conn);
        }
    }

    public synchronized boolean applyRoomMemberChanged(RoomMemberChanged event) {
        requireValidRoomMemberEvent(event);
        String action = event.action().trim().toUpperCase();
        String key = membershipKey(event.roomId(), event.userId());
        long version = event.version();
        MembershipProjectionEntry current = state.get().memberships().get(key);
        if (!isNewer(version, current == null ? null : current.version())) {
            return false;
        }

        Map<String, MembershipProjectionEntry> nextMemberships = new HashMap<>(state.get().memberships());
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

    private static void requireValidRoomMemberEvent(RoomMemberChanged event) {
        if (event == null) {
            throw new IllegalArgumentException("room member changed event must not be null");
        }
        if (event.eventId() == null || event.eventId().isBlank()) {
            throw new IllegalArgumentException("room member changed event must carry a non-blank eventId");
        }
        if (event.roomId() == null || event.userId() == null) {
            throw new IllegalArgumentException(
                    "room member changed event must carry a complete roomId/userId identity");
        }
        String action = event.action() == null ? "" : event.action().trim().toUpperCase();
        if (!"JOINED".equals(action) && !"LEFT".equals(action)) {
            throw new IllegalArgumentException("room member changed event has an unsupported action: " + event.action());
        }
    }

    private synchronized void replaceSnapshot(MembershipSnapshotClient.FetchedMembershipSnapshot snapshot) {
        if (snapshot == null) {
            return;
        }
        requireValidSnapshotEntries(snapshot);
        Map<String, MembershipProjectionEntry> currentMemberships = state.get().memberships();
        Map<String, MembershipProjectionEntry> nextMemberships = new HashMap<>(currentMemberships);
        Set<String> seenKeys = new HashSet<>();
        for (RoomMembershipEntry entry : snapshot.entries()) {
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
        for (Map.Entry<String, MembershipProjectionEntry> current : currentMemberships.entrySet()) {
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

    private static void requireValidSnapshotEntries(MembershipSnapshotClient.FetchedMembershipSnapshot snapshot) {
        if (snapshot.entries() == null) {
            throw new IllegalStateException("room membership snapshot omitted the entries list");
        }
        for (RoomMembershipEntry entry : snapshot.entries()) {
            if (entry == null || entry.roomId() == null || entry.userId() == null) {
                throw new IllegalStateException(
                        "room membership snapshot contained an entry without a complete roomId/userId identity");
            }
        }
    }

    private void replaceMembershipState(Map<String, MembershipProjectionEntry> nextMemberships) {
        Map<UUID, Set<UUID>> roomsByUser = new HashMap<>();
        Map<UUID, Set<UUID>> usersByRoom = new HashMap<>();
        for (MembershipProjectionEntry entry : nextMemberships.values()) {
            if (entry == null || !entry.active()) {
                continue;
            }
            roomsByUser.computeIfAbsent(entry.userId(), ignored -> new LinkedHashSet<>()).add(entry.roomId());
            usersByRoom.computeIfAbsent(entry.roomId(), ignored -> new LinkedHashSet<>()).add(entry.userId());
        }
        state.set(new MembershipProjectionState(
                Map.copyOf(nextMemberships),
                toImmutableCopy(roomsByUser),
                toImmutableCopy(usersByRoom)
        ));
    }

    private static Map<UUID, Set<UUID>> toImmutableCopy(Map<UUID, Set<UUID>> source) {
        Map<UUID, Set<UUID>> copy = new HashMap<>();
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

    private record MembershipProjectionState(
            Map<String, MembershipProjectionEntry> memberships,
            Map<UUID, Set<UUID>> roomIdsByUser,
            Map<UUID, Set<UUID>> memberIdsByRoom
    ) {

        private static MembershipProjectionState empty() {
            return new MembershipProjectionState(Map.of(), Map.of(), Map.of());
        }
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
