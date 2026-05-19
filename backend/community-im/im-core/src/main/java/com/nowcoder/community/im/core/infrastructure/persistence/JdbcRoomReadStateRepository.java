package com.nowcoder.community.im.core.infrastructure.persistence;

import com.nowcoder.community.common.id.BinaryUuidCodec;
import com.nowcoder.community.im.core.domain.repository.RoomReadStateRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public class JdbcRoomReadStateRepository implements RoomReadStateRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcRoomReadStateRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public long getLastReadSeq(UUID roomId, UUID userId) {
        Long v = jdbcTemplate.query(
                "select last_read_seq from im_room_read_state where room_id = ? and user_id = ?",
                rs -> rs.next() ? rs.getLong(1) : null,
                BinaryUuidCodec.toBytes(roomId),
                BinaryUuidCodec.toBytes(userId)
        );
        return v == null ? 0L : v;
    }

    @Override
    public void updateLastReadSeqMax(UUID roomId, UUID userId, long lastReadSeq) {
        byte[] userIdBytes = BinaryUuidCodec.toBytes(userId);
        int updated = jdbcTemplate.update(
                "update im_room_read_state set last_read_seq = greatest(last_read_seq, ?), updated_at = current_timestamp " +
                        "where room_id = ? and user_id = ?",
                lastReadSeq,
                BinaryUuidCodec.toBytes(roomId),
                userIdBytes
        );
        if (updated > 0) {
            return;
        }
        try {
            jdbcTemplate.update(
                    "insert into im_room_read_state(room_id, user_id, last_read_seq) values (?,?,?)",
                    BinaryUuidCodec.toBytes(roomId),
                    userIdBytes,
                    lastReadSeq
            );
        } catch (DuplicateKeyException ignore) {
            jdbcTemplate.update(
                    "update im_room_read_state set last_read_seq = greatest(last_read_seq, ?), updated_at = current_timestamp " +
                            "where room_id = ? and user_id = ?",
                    lastReadSeq,
                    BinaryUuidCodec.toBytes(roomId),
                    userIdBytes
            );
        }
    }
}
