package com.nowcoder.community.im.core.service;

import com.nowcoder.community.im.core.repository.RoomMemberRepository;
import com.nowcoder.community.im.core.repository.RoomRepository;
import com.nowcoder.community.im.core.support.AfterCommitExecutor;
import com.nowcoder.community.im.core.support.IdGenerator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

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
    public long createRoom(int creatorUserId, String name) {
        long roomId = idGenerator.nextId();
        roomRepository.insertRoom(roomId, name == null ? null : name.trim());
        roomMemberRepository.addMember(roomId, creatorUserId, ROLE_OWNER);
        AfterCommitExecutor.runAfterCommit(() -> changePublisher.publishJoined(roomId, creatorUserId));
        return roomId;
    }

    @Transactional
    public void joinRoom(int userId, long roomId) {
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
    public void leaveRoom(int userId, long roomId) {
        roomMemberRepository.removeMember(roomId, userId);
        AfterCommitExecutor.runAfterCommit(() -> changePublisher.publishLeft(roomId, userId));
    }

    public List<Long> listRoomIdsByUser(int userId, long cursorExclusive, int limit) {
        int l = Math.min(Math.max(1, limit), 5000);
        return roomMemberRepository.listRoomIdsByUser(userId, cursorExclusive, l);
    }

    public boolean isMember(long roomId, int userId) {
        return roomMemberRepository.isMember(roomId, userId);
    }
}
