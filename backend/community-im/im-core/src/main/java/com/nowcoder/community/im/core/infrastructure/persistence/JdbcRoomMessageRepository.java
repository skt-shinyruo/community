package com.nowcoder.community.im.core.infrastructure.persistence;

import com.nowcoder.community.common.id.BinaryUuidCodec;
import com.nowcoder.community.im.core.domain.model.RoomMessageRecord;
import com.nowcoder.community.im.core.domain.repository.RoomMessageRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcRoomMessageRepository implements RoomMessageRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcRoomMessageRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<RoomMessageRecord> findByIdempotency(UUID roomId, UUID fromUserId, String clientMsgId) {
        List<RoomMessageRecord> rows = jdbcTemplate.query(
                "select room_id, seq, message_id, from_user_id, content, client_msg_id, created_at " +
                        "from im_room_message where room_id = ? and from_user_id = ? and client_msg_id = ?",
                (rs, rowNum) -> new RoomMessageRecord(
                        BinaryUuidCodec.fromBytes(rs.getBytes("room_id")),
                        rs.getLong("seq"),
                        BinaryUuidCodec.fromBytes(rs.getBytes("message_id")),
                        BinaryUuidCodec.fromBytes(rs.getBytes("from_user_id")),
                        rs.getString("content"),
                        rs.getString("client_msg_id"),
                        rs.getTimestamp("created_at").toInstant()
                ),
                BinaryUuidCodec.toBytes(roomId),
                BinaryUuidCodec.toBytes(fromUserId),
                clientMsgId
        );
        return rows == null || rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    @Override
    public void insert(RoomMessageRecord row) {
        jdbcTemplate.update(
                "insert into im_room_message(room_id, seq, message_id, from_user_id, content, client_msg_id, created_at) " +
                        "values (?,?,?,?,?,?,?)",
                BinaryUuidCodec.toBytes(row.roomId()),
                row.seq(),
                BinaryUuidCodec.toBytes(row.messageId()),
                BinaryUuidCodec.toBytes(row.fromUserId()),
                row.content(),
                row.clientMsgId(),
                Timestamp.from(row.createdAt())
        );
    }

    @Override
    public List<RoomMessageRecord> listAfterSeq(UUID roomId, long afterSeqExclusive, int limit) {
        return jdbcTemplate.query(
                "select room_id, seq, message_id, from_user_id, content, client_msg_id, created_at " +
                        "from im_room_message where room_id = ? and seq > ? order by seq asc limit ?",
                (rs, rowNum) -> new RoomMessageRecord(
                        BinaryUuidCodec.fromBytes(rs.getBytes("room_id")),
                        rs.getLong("seq"),
                        BinaryUuidCodec.fromBytes(rs.getBytes("message_id")),
                        BinaryUuidCodec.fromBytes(rs.getBytes("from_user_id")),
                        rs.getString("content"),
                        rs.getString("client_msg_id"),
                        rs.getTimestamp("created_at").toInstant()
                ),
                BinaryUuidCodec.toBytes(roomId),
                afterSeqExclusive,
                limit
        );
    }
}
