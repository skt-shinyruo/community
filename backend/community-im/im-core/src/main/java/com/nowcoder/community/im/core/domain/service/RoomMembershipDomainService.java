package com.nowcoder.community.im.core.domain.service;

import com.nowcoder.community.im.core.domain.repository.RoomMemberRepository;
import com.nowcoder.community.im.core.domain.repository.RoomRepository;
import com.nowcoder.community.im.core.support.IdGenerator;
import com.nowcoder.community.im.common.projection.ProjectionVersions;
import com.nowcoder.community.im.common.projection.RoomMembershipEntry;
import com.nowcoder.community.im.common.projection.RoomMembershipSnapshot;

import java.util.List;
import java.util.UUID;

public class RoomMembershipDomainService {

    private static final int ROLE_MEMBER = 0;
    private static final int ROLE_OWNER = 1;

    private final RoomRepository roomRepository;
    private final RoomMemberRepository roomMemberRepository;
    private final IdGenerator idGenerator;
    private final int maxMembersPerRoom;

    public RoomMembershipDomainService(
            RoomRepository roomRepository,
            RoomMemberRepository roomMemberRepository,
            IdGenerator idGenerator,
            int maxMembersPerRoom
    ) {
        this.roomRepository = roomRepository;
        this.roomMemberRepository = roomMemberRepository;
        this.idGenerator = idGenerator;
        this.maxMembersPerRoom = Math.max(1, maxMembersPerRoom);
    }

    public UUID createRoom(UUID creatorUserId, String name) {
        UUID roomId = idGenerator.nextId();
        roomRepository.insertRoom(roomId, name == null ? null : name.trim());
        roomMemberRepository.addMember(roomId, creatorUserId, ROLE_OWNER);
        return roomId;
    }

    public boolean joinRoom(UUID userId, UUID roomId) {
        if (!roomRepository.exists(roomId)) {
            throw new IllegalArgumentException("room not found: " + roomId);
        }
        if (roomMemberRepository.isMember(roomId, userId)) {
            return false;
        }
        int members = roomMemberRepository.countMembers(roomId);
        if (members >= maxMembersPerRoom) {
            throw new IllegalStateException("room is full (max=" + maxMembersPerRoom + ")");
        }
        roomMemberRepository.addMember(roomId, userId, ROLE_MEMBER);
        return true;
    }

    public void leaveRoom(UUID userId, UUID roomId) {
        roomMemberRepository.removeMember(roomId, userId);
    }

    public List<UUID> listRoomIdsByUser(UUID userId, UUID cursorExclusive, int limit) {
        int l = Math.min(Math.max(1, limit), 5000);
        return roomMemberRepository.listRoomIdsByUser(userId, cursorExclusive, l);
    }

    public RoomMembershipSnapshot snapshot(UUID afterRoomId, UUID afterUserId, int limit) {
        if ((afterRoomId == null) != (afterUserId == null)) {
            throw new IllegalArgumentException("afterRoomId and afterUserId must be provided together");
        }
        long snapshotHighWatermark = ProjectionVersions.snapshotHighWatermarkFromEpochMillis(System.currentTimeMillis());
        int l = Math.min(500, Math.max(1, limit));
        List<RoomMembershipEntry> scannedEntries = roomMemberRepository.scanMemberships(afterRoomId, afterUserId, l + 1);
        boolean hasMore = scannedEntries.size() > l;
        List<RoomMembershipEntry> pageEntries = (hasMore ? scannedEntries.subList(0, l) : scannedEntries).stream()
                .filter(entry -> entry != null && entry.roomId() != null && entry.userId() != null)
                .map(entry -> withSnapshotVersion(entry, snapshotHighWatermark))
                .toList();
        RoomMembershipEntry last = pageEntries.isEmpty() ? null : pageEntries.get(pageEntries.size() - 1);
        return new RoomMembershipSnapshot(
                pageEntries,
                last == null ? null : last.roomId(),
                last == null ? null : last.userId(),
                hasMore,
                snapshotHighWatermark
        );
    }

    public boolean isMember(UUID roomId, UUID userId) {
        return roomMemberRepository.isMember(roomId, userId);
    }

    private RoomMembershipEntry withSnapshotVersion(RoomMembershipEntry entry, long snapshotHighWatermark) {
        return new RoomMembershipEntry(
                entry.roomId(),
                entry.userId(),
                snapshotHighWatermark,
                null
        );
    }
}
