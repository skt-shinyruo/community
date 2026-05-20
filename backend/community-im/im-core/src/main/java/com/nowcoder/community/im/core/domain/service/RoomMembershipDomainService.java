package com.nowcoder.community.im.core.domain.service;

import com.nowcoder.community.im.core.domain.repository.RoomMemberRepository;
import com.nowcoder.community.im.core.domain.repository.RoomRepository;
import com.nowcoder.community.im.core.support.IdGenerator;
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

    public MembershipChange createRoom(UUID creatorUserId, String name) {
        UUID roomId = idGenerator.nextId();
        roomRepository.insertRoom(roomId, name == null ? null : name.trim());
        long version = roomMemberRepository.addMember(roomId, creatorUserId, ROLE_OWNER);
        return new MembershipChange(roomId, creatorUserId, version, true);
    }

    public MembershipChange joinRoom(UUID userId, UUID roomId) {
        if (!roomRepository.exists(roomId)) {
            throw new IllegalArgumentException("room not found: " + roomId);
        }
        if (roomMemberRepository.isMember(roomId, userId)) {
            return MembershipChange.unchanged(roomId, userId);
        }
        int members = roomMemberRepository.countMembers(roomId);
        if (members >= maxMembersPerRoom) {
            throw new IllegalStateException("room is full (max=" + maxMembersPerRoom + ")");
        }
        long version = roomMemberRepository.addMember(roomId, userId, ROLE_MEMBER);
        return new MembershipChange(roomId, userId, version, true);
    }

    public MembershipChange leaveRoom(UUID userId, UUID roomId) {
        long version = roomMemberRepository.removeMember(roomId, userId);
        return version > 0L
                ? new MembershipChange(roomId, userId, version, true)
                : MembershipChange.unchanged(roomId, userId);
    }

    public List<UUID> listRoomIdsByUser(UUID userId, UUID cursorExclusive, int limit) {
        int l = Math.min(Math.max(1, limit), 5000);
        return roomMemberRepository.listRoomIdsByUser(userId, cursorExclusive, l);
    }

    public RoomMembershipSnapshot snapshot(UUID afterRoomId, UUID afterUserId, int limit) {
        if ((afterRoomId == null) != (afterUserId == null)) {
            throw new IllegalArgumentException("afterRoomId and afterUserId must be provided together");
        }
        long snapshotHighWatermark = roomMemberRepository.currentMembershipProjectionVersion();
        int l = Math.min(500, Math.max(1, limit));
        List<RoomMembershipEntry> scannedEntries = roomMemberRepository.scanMemberships(afterRoomId, afterUserId, l + 1);
        boolean hasMore = scannedEntries.size() > l;
        List<RoomMembershipEntry> pageEntries = (hasMore ? scannedEntries.subList(0, l) : scannedEntries).stream()
                .filter(entry -> entry != null && entry.roomId() != null && entry.userId() != null)
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

    public record MembershipChange(UUID roomId, UUID userId, long version, boolean changed) {

        public static MembershipChange unchanged(UUID roomId, UUID userId) {
            return new MembershipChange(roomId, userId, 0L, false);
        }
    }
}
