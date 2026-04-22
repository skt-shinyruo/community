package com.nowcoder.community.im.core.repository;

import com.nowcoder.community.common.id.BinaryUuidCodec;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public class RoomMemberRepository {

    private final JdbcTemplate jdbcTemplate;

    public RoomMemberRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean isMember(UUID roomId, UUID userId) {
        Integer n = jdbcTemplate.queryForObject(
                "select count(1) from im_room_member where room_id = ? and user_id = ?",
                Integer.class,
                BinaryUuidCodec.toBytes(roomId),
                BinaryUuidCodec.toBytes(userId)
        );
        return n != null && n > 0;
    }

    public int countMembers(UUID roomId) {
        Integer n = jdbcTemplate.queryForObject(
                "select count(1) from im_room_member where room_id = ?",
                Integer.class,
                BinaryUuidCodec.toBytes(roomId)
        );
        return n == null ? 0 : n;
    }

    public void addMember(UUID roomId, UUID userId, int role) {
        try {
            jdbcTemplate.update(
                    "insert into im_room_member(room_id, user_id, role) values (?,?,?)",
                    BinaryUuidCodec.toBytes(roomId),
                    BinaryUuidCodec.toBytes(userId),
                    role
            );
        } catch (DuplicateKeyException ignore) {
            // idempotent join
        }
    }

    public void removeMember(UUID roomId, UUID userId) {
        jdbcTemplate.update(
                "delete from im_room_member where room_id = ? and user_id = ?",
                BinaryUuidCodec.toBytes(roomId),
                BinaryUuidCodec.toBytes(userId)
        );
    }

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
}
