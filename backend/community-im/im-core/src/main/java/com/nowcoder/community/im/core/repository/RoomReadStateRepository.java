package com.nowcoder.community.im.core.repository;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class RoomReadStateRepository {

    private final JdbcTemplate jdbcTemplate;

    public RoomReadStateRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public long getLastReadSeq(long roomId, int userId) {
        Long v = jdbcTemplate.query(
                "select last_read_seq from im_room_read_state where room_id = ? and user_id = ?",
                rs -> rs.next() ? rs.getLong(1) : null,
                roomId,
                userId
        );
        return v == null ? 0L : v;
    }

    public void updateLastReadSeqMax(long roomId, int userId, long lastReadSeq) {
        int updated = jdbcTemplate.update(
                "update im_room_read_state set last_read_seq = greatest(last_read_seq, ?), updated_at = current_timestamp " +
                        "where room_id = ? and user_id = ?",
                lastReadSeq,
                roomId,
                userId
        );
        if (updated > 0) {
            return;
        }
        try {
            jdbcTemplate.update(
                    "insert into im_room_read_state(room_id, user_id, last_read_seq) values (?,?,?)",
                    roomId,
                    userId,
                    lastReadSeq
            );
        } catch (DuplicateKeyException ignore) {
            jdbcTemplate.update(
                    "update im_room_read_state set last_read_seq = greatest(last_read_seq, ?), updated_at = current_timestamp " +
                            "where room_id = ? and user_id = ?",
                    lastReadSeq,
                    roomId,
                    userId
            );
        }
    }
}

