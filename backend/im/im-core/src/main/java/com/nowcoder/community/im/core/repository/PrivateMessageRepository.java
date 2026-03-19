package com.nowcoder.community.im.core.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public class PrivateMessageRepository {

    private final JdbcTemplate jdbcTemplate;

    public PrivateMessageRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<PrivateMessageRow> findByIdempotency(String conversationId, int fromUserId, String clientMsgId) {
        List<PrivateMessageRow> rows = jdbcTemplate.query(
                "select conversation_id, seq, message_id, from_user_id, to_user_id, content, client_msg_id, created_at " +
                        "from im_private_message where conversation_id = ? and from_user_id = ? and client_msg_id = ?",
                (rs, rowNum) -> new PrivateMessageRow(
                        rs.getString("conversation_id"),
                        rs.getLong("seq"),
                        rs.getLong("message_id"),
                        rs.getInt("from_user_id"),
                        rs.getInt("to_user_id"),
                        rs.getString("content"),
                        rs.getString("client_msg_id"),
                        rs.getTimestamp("created_at").toInstant()
                ),
                conversationId,
                fromUserId,
                clientMsgId
        );
        return rows == null || rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    public void insert(PrivateMessageRow row) {
        jdbcTemplate.update(
                "insert into im_private_message(conversation_id, seq, message_id, from_user_id, to_user_id, content, client_msg_id, created_at) " +
                        "values (?,?,?,?,?,?,?,?)",
                row.conversationId(),
                row.seq(),
                row.messageId(),
                row.fromUserId(),
                row.toUserId(),
                row.content(),
                row.clientMsgId(),
                Timestamp.from(row.createdAt())
        );
    }

    public List<PrivateMessageRow> listAfterSeq(String conversationId, long afterSeqExclusive, int limit) {
        return jdbcTemplate.query(
                "select conversation_id, seq, message_id, from_user_id, to_user_id, content, client_msg_id, created_at " +
                        "from im_private_message where conversation_id = ? and seq > ? order by seq asc limit ?",
                (rs, rowNum) -> new PrivateMessageRow(
                        rs.getString("conversation_id"),
                        rs.getLong("seq"),
                        rs.getLong("message_id"),
                        rs.getInt("from_user_id"),
                        rs.getInt("to_user_id"),
                        rs.getString("content"),
                        rs.getString("client_msg_id"),
                        rs.getTimestamp("created_at").toInstant()
                ),
                conversationId,
                afterSeqExclusive,
                limit
        );
    }

    public record PrivateMessageRow(
            String conversationId,
            long seq,
            long messageId,
            int fromUserId,
            int toUserId,
            String content,
            String clientMsgId,
            Instant createdAt
    ) {
    }
}

