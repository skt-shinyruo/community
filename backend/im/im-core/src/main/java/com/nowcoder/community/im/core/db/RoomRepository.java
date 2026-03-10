package com.nowcoder.community.im.core.db;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class RoomRepository {

    private final JdbcTemplate jdbcTemplate;

    public RoomRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean exists(long roomId) {
        Integer n = jdbcTemplate.queryForObject(
                "select count(1) from im_room where room_id = ?",
                Integer.class,
                roomId
        );
        return n != null && n > 0;
    }

    public void insertRoom(long roomId, String name) {
        try {
            jdbcTemplate.update(
                    "insert into im_room(room_id, name, last_seq) values (?,?,0)",
                    roomId,
                    name
            );
        } catch (DuplicateKeyException e) {
            throw new IllegalArgumentException("room already exists: " + roomId);
        }
    }

    public long selectLastSeqForUpdate(long roomId) {
        Long v = jdbcTemplate.queryForObject(
                "select last_seq from im_room where room_id = ? for update",
                Long.class,
                roomId
        );
        if (v == null) {
            throw new IllegalArgumentException("room not found: " + roomId);
        }
        return v;
    }

    public void updateLastSeq(long roomId, long lastSeq) {
        jdbcTemplate.update(
                "update im_room set last_seq = ?, updated_at = current_timestamp where room_id = ?",
                lastSeq,
                roomId
        );
    }
}

