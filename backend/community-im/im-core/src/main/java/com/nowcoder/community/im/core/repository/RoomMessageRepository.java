package com.nowcoder.community.im.core.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public class RoomMessageRepository {

    private final JdbcTemplate jdbcTemplate;

    public RoomMessageRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<RoomMessageRow> findByIdempotency(long roomId, int fromUserId, String clientMsgId) {
        List<RoomMessageRow> rows = jdbcTemplate.query(
                "select room_id, seq, message_id, from_user_id, content, client_msg_id, created_at " +
                        "from im_room_message where room_id = ? and from_user_id = ? and client_msg_id = ?",
                (rs, rowNum) -> new RoomMessageRow(
                        rs.getLong("room_id"),
                        rs.getLong("seq"),
                        rs.getLong("message_id"),
                        rs.getInt("from_user_id"),
                        rs.getString("content"),
                        rs.getString("client_msg_id"),
                        rs.getTimestamp("created_at").toInstant()
                ),
                roomId,
                fromUserId,
                clientMsgId
        );
        return rows == null || rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    public void insert(RoomMessageRow row) {
        jdbcTemplate.update(
                "insert into im_room_message(room_id, seq, message_id, from_user_id, content, client_msg_id, created_at) " +
                        "values (?,?,?,?,?,?,?)",
                row.roomId(),
                row.seq(),
                row.messageId(),
                row.fromUserId(),
                row.content(),
                row.clientMsgId(),
                Timestamp.from(row.createdAt())
        );
    }

    public List<RoomMessageRow> listAfterSeq(long roomId, long afterSeqExclusive, int limit) {
        return jdbcTemplate.query(
                "select room_id, seq, message_id, from_user_id, content, client_msg_id, created_at " +
                        "from im_room_message where room_id = ? and seq > ? order by seq asc limit ?",
                (rs, rowNum) -> new RoomMessageRow(
                        rs.getLong("room_id"),
                        rs.getLong("seq"),
                        rs.getLong("message_id"),
                        rs.getInt("from_user_id"),
                        rs.getString("content"),
                        rs.getString("client_msg_id"),
                        rs.getTimestamp("created_at").toInstant()
                ),
                roomId,
                afterSeqExclusive,
                limit
        );
    }

    public record RoomMessageRow(
            long roomId,
            long seq,
            long messageId,
            int fromUserId,
            String content,
            String clientMsgId,
            Instant createdAt
    ) {
    }
}

