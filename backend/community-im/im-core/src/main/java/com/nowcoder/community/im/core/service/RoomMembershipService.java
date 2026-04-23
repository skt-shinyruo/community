package com.nowcoder.community.im.core.service;

import com.nowcoder.community.im.core.repository.RoomMemberRepository;
import com.nowcoder.community.im.core.repository.RoomRepository;
import com.nowcoder.community.im.core.support.IdGenerator;
import com.nowcoder.community.common.tx.AfterCommitExecutor;
import com.nowcoder.community.im.common.projection.RoomMembershipEntry;
import com.nowcoder.community.im.common.projection.RoomMembershipSnapshot;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class RoomMembershipService {

    private static final int ROLE_MEMBER = 0;
    private static final int ROLE_OWNER = 1;

    private final RoomRepository roomRepository;
    private final RoomMemberRepository roomMemberRepository;
    private final IdGenerator idGenerator;
    private final RoomMemberChangePublisher changePublisher;
    private final int maxMembersPerRoom;

    public RoomMembershipService(
            RoomRepository roomRepository,
            RoomMemberRepository roomMemberRepository,
            IdGenerator idGenerator,
            RoomMemberChangePublisher changePublisher,
            @Value("${im.room.max-members:10000}") int maxMembersPerRoom
    ) {
        this.roomRepository = roomRepository;
        this.roomMemberRepository = roomMemberRepository;
        this.idGenerator = idGenerator;
        this.changePublisher = changePublisher;
        this.maxMembersPerRoom = Math.max(1, maxMembersPerRoom);
    }

    @Transactional
    public UUID createRoom(UUID creatorUserId, String name) {
        UUID roomId = idGenerator.nextId();
        roomRepository.insertRoom(roomId, name == null ? null : name.trim());
        roomMemberRepository.addMember(roomId, creatorUserId, ROLE_OWNER);
        AfterCommitExecutor.runAfterCommit(() -> changePublisher.publishJoined(roomId, creatorUserId));
        return roomId;
    }

    @Transactional
    public void joinRoom(UUID userId, UUID roomId) {
        if (!roomRepository.exists(roomId)) {
            throw new IllegalArgumentException("room not found: " + roomId);
        }
        if (roomMemberRepository.isMember(roomId, userId)) {
            return;
        }
        int members = roomMemberRepository.countMembers(roomId);
        if (members >= maxMembersPerRoom) {
            throw new IllegalStateException("room is full (max=" + maxMembersPerRoom + ")");
        }
        roomMemberRepository.addMember(roomId, userId, ROLE_MEMBER);
        AfterCommitExecutor.runAfterCommit(() -> changePublisher.publishJoined(roomId, userId));
    }

    @Transactional
    public void leaveRoom(UUID userId, UUID roomId) {
        roomMemberRepository.removeMember(roomId, userId);
        AfterCommitExecutor.runAfterCommit(() -> changePublisher.publishLeft(roomId, userId));
    }

    public List<UUID> listRoomIdsByUser(UUID userId, UUID cursorExclusive, int limit) {
        int l = Math.min(Math.max(1, limit), 5000);
        return roomMemberRepository.listRoomIdsByUser(userId, cursorExclusive, l);
    }

    @Transactional(readOnly = true)
    public RoomMembershipSnapshot snapshot(UUID afterRoomId, UUID afterUserId, int limit) {
        if ((afterRoomId == null) != (afterUserId == null)) {
            throw new IllegalArgumentException("afterRoomId and afterUserId must be provided together");
        }
        int l = Math.min(500, Math.max(1, limit));
        List<RoomMembershipEntry> scannedEntries = roomMemberRepository.scanMemberships(afterRoomId, afterUserId, l + 1);
        boolean hasMore = scannedEntries.size() > l;
        List<RoomMembershipEntry> pageEntries = hasMore ? scannedEntries.subList(0, l) : scannedEntries;
        RoomMembershipEntry last = pageEntries.isEmpty() ? null : pageEntries.get(pageEntries.size() - 1);
        return new RoomMembershipSnapshot(
                pageEntries,
                last == null ? null : last.roomId(),
                last == null ? null : last.userId(),
                hasMore
        );
    }

    public boolean isMember(UUID roomId, UUID userId) {
        return roomMemberRepository.isMember(roomId, userId);
    }
}
