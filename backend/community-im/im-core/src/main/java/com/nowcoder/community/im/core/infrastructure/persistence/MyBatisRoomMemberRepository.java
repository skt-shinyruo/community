package com.nowcoder.community.im.core.infrastructure.persistence;

import com.nowcoder.community.im.common.projection.RoomMembershipEntry;
import com.nowcoder.community.im.core.domain.repository.RoomMemberRepository;
import com.nowcoder.community.im.core.infrastructure.persistence.mapper.RoomMemberMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public class MyBatisRoomMemberRepository implements RoomMemberRepository {

    private static final int MEMBERSHIP_VERSION_COUNTER_ID = 1;

    private final RoomMemberMapper mapper;

    public MyBatisRoomMemberRepository(RoomMemberMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public boolean isMember(UUID roomId, UUID userId) {
        return mapper.countMembership(roomId, userId) > 0;
    }

    @Override
    public int countMembers(UUID roomId) {
        return mapper.countMembers(roomId);
    }

    @Override
    public long addMember(UUID roomId, UUID userId, int role) {
        long version = nextMembershipProjectionVersion();
        try {
            int inserted = mapper.insertMember(roomId, userId, role, version);
            if (inserted > 0) {
                insertMembershipVersionLog(version, roomId, userId, true);
                return version;
            }
        } catch (DuplicateKeyException ignore) {
            // idempotent join
        }
        return 0L;
    }

    @Override
    public long removeMember(UUID roomId, UUID userId) {
        long version = nextMembershipProjectionVersion();
        int deleted = mapper.deleteMember(roomId, userId);
        if (deleted > 0) {
            insertMembershipVersionLog(version, roomId, userId, false);
            return version;
        }
        return 0L;
    }

    @Override
    public List<UUID> listRoomIdsByUser(UUID userId, UUID cursorRoomIdExclusive, int limit) {
        return mapper.selectRoomIdsByUser(userId, cursorRoomIdExclusive, limit);
    }

    @Override
    public List<RoomMembershipEntry> scanMemberships(UUID afterRoomId, UUID afterUserId, int limit) {
        UUID roomCursor = afterRoomId == null ? new UUID(0L, 0L) : afterRoomId;
        UUID userCursor = afterUserId == null ? new UUID(0L, 0L) : afterUserId;
        int l = Math.min(501, Math.max(1, limit));
        return mapper.scanMemberships(roomCursor, userCursor, l);
    }

    @Override
    public long currentMembershipProjectionVersion() {
        Long value = mapper.selectCurrentMembershipProjectionVersion(MEMBERSHIP_VERSION_COUNTER_ID);
        return value == null ? 0L : value;
    }

    private long nextMembershipProjectionVersion() {
        ensureMembershipVersionCounter();
        Long current = mapper.selectMembershipVersionForUpdate(MEMBERSHIP_VERSION_COUNTER_ID);
        long next = Math.addExact(current == null ? 0L : current, 1L);
        mapper.updateMembershipVersion(MEMBERSHIP_VERSION_COUNTER_ID, next);
        return next;
    }

    private void ensureMembershipVersionCounter() {
        try {
            mapper.insertMembershipVersionCounter(MEMBERSHIP_VERSION_COUNTER_ID);
        } catch (DuplicateKeyException ignored) {
            // already initialized
        }
    }

    private void insertMembershipVersionLog(long version, UUID roomId, UUID userId, boolean active) {
        mapper.insertMembershipVersionLog(version, roomId, userId, active);
    }
}
