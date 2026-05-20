package com.nowcoder.community.im.core.infrastructure.persistence;

import com.nowcoder.community.common.id.BinaryUuidCodec;
import com.nowcoder.community.im.common.projection.RoomMembershipEntry;
import com.nowcoder.community.im.core.domain.repository.RoomMemberRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public class JdbcRoomMemberRepository implements RoomMemberRepository {

    private static final int MEMBERSHIP_VERSION_COUNTER_ID = 1;
    private static final int LEGACY_COMPATIBLE_LOGICAL_BITS = 12;

    private final JdbcTemplate jdbcTemplate;

    public JdbcRoomMemberRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public boolean isMember(UUID roomId, UUID userId) {
        Integer n = jdbcTemplate.queryForObject(
                "select count(1) from im_room_member where room_id = ? and user_id = ?",
                Integer.class,
                BinaryUuidCodec.toBytes(roomId),
                BinaryUuidCodec.toBytes(userId)
        );
        return n != null && n > 0;
    }

    @Override
    public int countMembers(UUID roomId) {
        Integer n = jdbcTemplate.queryForObject(
                "select count(1) from im_room_member where room_id = ?",
                Integer.class,
                BinaryUuidCodec.toBytes(roomId)
        );
        return n == null ? 0 : n;
    }

    @Override
    public long addMember(UUID roomId, UUID userId, int role) {
        long version = nextMembershipProjectionVersion();
        try {
            int inserted = jdbcTemplate.update(
                    "insert into im_room_member(room_id, user_id, role, version) values (?,?,?,?)",
                    BinaryUuidCodec.toBytes(roomId),
                    BinaryUuidCodec.toBytes(userId),
                    role,
                    version
            );
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
        int deleted = jdbcTemplate.update(
                "delete from im_room_member where room_id = ? and user_id = ?",
                BinaryUuidCodec.toBytes(roomId),
                BinaryUuidCodec.toBytes(userId)
        );
        if (deleted > 0) {
            insertMembershipVersionLog(version, roomId, userId, false);
            return version;
        }
        return 0L;
    }

    @Override
    public List<UUID> listRoomIdsByUser(UUID userId, UUID cursorRoomIdExclusive, int limit) {
        byte[] userIdBytes = BinaryUuidCodec.toBytes(userId);
        if (cursorRoomIdExclusive == null) {
            return jdbcTemplate.query(
                    "select room_id from im_room_member where user_id = ? order by room_id asc limit ?",
                    (rs, rowNum) -> BinaryUuidCodec.fromBytes(rs.getBytes(1)),
                    userIdBytes,
                    limit
            );
        }
        return jdbcTemplate.query(
                "select room_id from im_room_member where user_id = ? and room_id > ? order by room_id asc limit ?",
                (rs, rowNum) -> BinaryUuidCodec.fromBytes(rs.getBytes(1)),
                userIdBytes,
                BinaryUuidCodec.toBytes(cursorRoomIdExclusive),
                limit
        );
    }

    @Override
    public List<RoomMembershipEntry> scanMemberships(UUID afterRoomId, UUID afterUserId, int limit) {
        UUID roomCursor = afterRoomId == null ? new UUID(0L, 0L) : afterRoomId;
        UUID userCursor = afterUserId == null ? new UUID(0L, 0L) : afterUserId;
        int l = Math.min(501, Math.max(1, limit));
        return jdbcTemplate.query(
                """
                        select room_id, user_id, version
                        from im_room_member
                        where (room_id > ?) or (room_id = ? and user_id > ?)
                        order by room_id asc, user_id asc
                        limit ?
                        """,
                (rs, rowNum) -> new RoomMembershipEntry(
                        BinaryUuidCodec.fromBytes(rs.getBytes("room_id")),
                        BinaryUuidCodec.fromBytes(rs.getBytes("user_id")),
                        rs.getLong("version"),
                        null
                ),
                BinaryUuidCodec.toBytes(roomCursor),
                BinaryUuidCodec.toBytes(roomCursor),
                BinaryUuidCodec.toBytes(userCursor),
                l
        );
    }

    @Override
    public long currentMembershipProjectionVersion() {
        Long value = jdbcTemplate.queryForObject(
                "select coalesce(max(current_version), 0) from im_membership_version_counter where id = ?",
                Long.class,
                MEMBERSHIP_VERSION_COUNTER_ID
        );
        return value == null ? 0L : value;
    }

    private long nextMembershipProjectionVersion() {
        ensureMembershipVersionCounter();
        Long current = jdbcTemplate.queryForObject(
                "select current_version from im_membership_version_counter where id = ? for update",
                Long.class,
                MEMBERSHIP_VERSION_COUNTER_ID
        );
        long next = Math.max((current == null ? 0L : current) + 1L, legacyCompatibleVersionFloor());
        jdbcTemplate.update(
                "update im_membership_version_counter set current_version = ? where id = ?",
                next,
                MEMBERSHIP_VERSION_COUNTER_ID
        );
        return next;
    }

    private void ensureMembershipVersionCounter() {
        try {
            jdbcTemplate.update(
                    "insert into im_membership_version_counter(id, current_version) values (?, 0)",
                    MEMBERSHIP_VERSION_COUNTER_ID
            );
        } catch (DuplicateKeyException ignored) {
            // already initialized
        }
    }

    private void insertMembershipVersionLog(long version, UUID roomId, UUID userId, boolean active) {
        jdbcTemplate.update(
                """
                        insert into im_membership_version_log(version, room_id, user_id, active)
                        values (?, ?, ?, ?)
                        """,
                version,
                BinaryUuidCodec.toBytes(roomId),
                BinaryUuidCodec.toBytes(userId),
                active
        );
    }

    private static long legacyCompatibleVersionFloor() {
        long epochMillis = System.currentTimeMillis();
        return epochMillis <= 0L ? 1L : epochMillis << LEGACY_COMPATIBLE_LOGICAL_BITS;
    }
}
