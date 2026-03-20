package com.nowcoder.community.im.core.repository;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class RoomMemberRepository {

    private final JdbcTemplate jdbcTemplate;

    public RoomMemberRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean isMember(long roomId, int userId) {
        Integer n = jdbcTemplate.queryForObject(
                "select count(1) from im_room_member where room_id = ? and user_id = ?",
                Integer.class,
                roomId,
                userId
        );
        return n != null && n > 0;
    }

    public int countMembers(long roomId) {
        Integer n = jdbcTemplate.queryForObject(
                "select count(1) from im_room_member where room_id = ?",
                Integer.class,
                roomId
        );
        return n == null ? 0 : n;
    }

    public void addMember(long roomId, int userId, int role) {
        try {
            jdbcTemplate.update(
                    "insert into im_room_member(room_id, user_id, role) values (?,?,?)",
                    roomId,
                    userId,
                    role
            );
        } catch (DuplicateKeyException ignore) {
            // idempotent join
        }
    }

    public void removeMember(long roomId, int userId) {
        jdbcTemplate.update(
                "delete from im_room_member where room_id = ? and user_id = ?",
                roomId,
                userId
        );
    }

    public List<Long> listRoomIdsByUser(int userId, long cursorRoomIdExclusive, int limit) {
        return jdbcTemplate.query(
                "select room_id from im_room_member where user_id = ? and room_id > ? order by room_id asc limit ?",
                (rs, rowNum) -> rs.getLong(1),
                userId,
                cursorRoomIdExclusive,
                limit
        );
    }
}

