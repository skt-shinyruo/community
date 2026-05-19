package com.nowcoder.community.im.core.infrastructure.persistence;

import com.nowcoder.community.common.id.BinaryUuidCodec;
import com.nowcoder.community.im.core.domain.model.PrivateMessageRecord;
import com.nowcoder.community.im.core.domain.repository.PrivateMessageRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcPrivateMessageRepository implements PrivateMessageRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcPrivateMessageRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<PrivateMessageRecord> findByIdempotency(String conversationId, UUID fromUserId, String clientMsgId) {
        List<PrivateMessageRecord> rows = jdbcTemplate.query(
                "select conversation_id, seq, message_id, from_user_id, to_user_id, content, client_msg_id, created_at " +
                        "from im_private_message where conversation_id = ? and from_user_id = ? and client_msg_id = ?",
                (rs, rowNum) -> new PrivateMessageRecord(
                        rs.getString("conversation_id"),
                        rs.getLong("seq"),
                        BinaryUuidCodec.fromBytes(rs.getBytes("message_id")),
                        BinaryUuidCodec.fromBytes(rs.getBytes("from_user_id")),
                        BinaryUuidCodec.fromBytes(rs.getBytes("to_user_id")),
                        rs.getString("content"),
                        rs.getString("client_msg_id"),
                        rs.getTimestamp("created_at").toInstant()
                ),
                conversationId,
                BinaryUuidCodec.toBytes(fromUserId),
                clientMsgId
        );
        return rows == null || rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    @Override
    public void insert(PrivateMessageRecord row) {
        jdbcTemplate.update(
                "insert into im_private_message(conversation_id, seq, message_id, from_user_id, to_user_id, content, client_msg_id, created_at) " +
                        "values (?,?,?,?,?,?,?,?)",
                row.conversationId(),
                row.seq(),
                BinaryUuidCodec.toBytes(row.messageId()),
                BinaryUuidCodec.toBytes(row.fromUserId()),
                BinaryUuidCodec.toBytes(row.toUserId()),
                row.content(),
                row.clientMsgId(),
                Timestamp.from(row.createdAt())
        );
    }

    @Override
    public List<PrivateMessageRecord> listAfterSeq(String conversationId, long afterSeqExclusive, int limit) {
        return jdbcTemplate.query(
                "select conversation_id, seq, message_id, from_user_id, to_user_id, content, client_msg_id, created_at " +
                        "from im_private_message where conversation_id = ? and seq > ? order by seq asc limit ?",
                (rs, rowNum) -> new PrivateMessageRecord(
                        rs.getString("conversation_id"),
                        rs.getLong("seq"),
                        BinaryUuidCodec.fromBytes(rs.getBytes("message_id")),
                        BinaryUuidCodec.fromBytes(rs.getBytes("from_user_id")),
                        BinaryUuidCodec.fromBytes(rs.getBytes("to_user_id")),
                        rs.getString("content"),
                        rs.getString("client_msg_id"),
                        rs.getTimestamp("created_at").toInstant()
                ),
                conversationId,
                afterSeqExclusive,
                limit
        );
    }
}
