package com.nowcoder.community.im.core.repository;

import com.nowcoder.community.common.id.BinaryUuidCodec;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public class ConversationReadStateRepository {

    private final JdbcTemplate jdbcTemplate;

    public ConversationReadStateRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public long getLastReadSeq(String conversationId, UUID userId) {
        Long v = jdbcTemplate.query(
                "select last_read_seq from im_conversation_read_state where conversation_id = ? and user_id = ?",
                rs -> rs.next() ? rs.getLong(1) : null,
                conversationId,
                BinaryUuidCodec.toBytes(userId)
        );
        return v == null ? 0L : v;
    }

    public void updateLastReadSeqMax(String conversationId, UUID userId, long lastReadSeq) {
        byte[] userIdBytes = BinaryUuidCodec.toBytes(userId);
        int updated = jdbcTemplate.update(
                "update im_conversation_read_state set last_read_seq = greatest(last_read_seq, ?), updated_at = current_timestamp " +
                        "where conversation_id = ? and user_id = ?",
                lastReadSeq,
                conversationId,
                userIdBytes
        );
        if (updated > 0) {
            return;
        }
        try {
            jdbcTemplate.update(
                    "insert into im_conversation_read_state(conversation_id, user_id, last_read_seq) values (?,?,?)",
                    conversationId,
                    userIdBytes,
                    lastReadSeq
            );
        } catch (DuplicateKeyException ignore) {
            jdbcTemplate.update(
                    "update im_conversation_read_state set last_read_seq = greatest(last_read_seq, ?), updated_at = current_timestamp " +
                            "where conversation_id = ? and user_id = ?",
                    lastReadSeq,
                    conversationId,
                    userIdBytes
            );
        }
    }
}
