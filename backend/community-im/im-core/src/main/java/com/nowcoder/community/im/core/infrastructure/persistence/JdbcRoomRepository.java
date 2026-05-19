package com.nowcoder.community.im.core.infrastructure.persistence;

import com.nowcoder.community.common.id.BinaryUuidCodec;
import com.nowcoder.community.im.core.domain.repository.RoomRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public class JdbcRoomRepository implements RoomRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcRoomRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public boolean exists(UUID roomId) {
        Integer n = jdbcTemplate.queryForObject(
                "select count(1) from im_room where room_id = ?",
                Integer.class,
                BinaryUuidCodec.toBytes(roomId)
        );
        return n != null && n > 0;
    }

    @Override
    public void insertRoom(UUID roomId, String name) {
        try {
            jdbcTemplate.update(
                    "insert into im_room(room_id, name, last_seq) values (?,?,0)",
                    BinaryUuidCodec.toBytes(roomId),
                    name
            );
        } catch (DuplicateKeyException e) {
            throw new IllegalArgumentException("room already exists: " + roomId);
        }
    }

    @Override
    public long selectLastSeqForUpdate(UUID roomId) {
        Long v = jdbcTemplate.queryForObject(
                "select last_seq from im_room where room_id = ? for update",
                Long.class,
                BinaryUuidCodec.toBytes(roomId)
        );
        if (v == null) {
            throw new IllegalArgumentException("room not found: " + roomId);
        }
        return v;
    }

    @Override
    public void updateLastSeq(UUID roomId, long lastSeq) {
        jdbcTemplate.update(
                "update im_room set last_seq = ?, updated_at = current_timestamp where room_id = ?",
                lastSeq,
                BinaryUuidCodec.toBytes(roomId)
        );
    }
}
