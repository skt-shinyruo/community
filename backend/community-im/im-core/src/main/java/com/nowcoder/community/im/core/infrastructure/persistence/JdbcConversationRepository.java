package com.nowcoder.community.im.core.infrastructure.persistence;

import com.nowcoder.community.common.id.BinaryUuidCodec;
import com.nowcoder.community.im.core.domain.repository.ConversationRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public class JdbcConversationRepository implements ConversationRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcConversationRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public boolean exists(String conversationId) {
        Integer n = jdbcTemplate.queryForObject(
                "select count(1) from im_conversation where conversation_id = ?",
                Integer.class,
                conversationId
        );
        return n != null && n > 0;
    }

    @Override
    public void ensureExists(String conversationId, UUID userA, UUID userB) {
        try {
            jdbcTemplate.update(
                    "insert into im_conversation(conversation_id, user_a, user_b, last_seq) values (?,?,?,0)",
                    conversationId,
                    BinaryUuidCodec.toBytes(userA),
                    BinaryUuidCodec.toBytes(userB)
            );
        } catch (DuplicateKeyException ignore) {
            // idempotent: already exists
        }
    }

    @Override
    public long selectLastSeqForUpdate(String conversationId) {
        Long v = jdbcTemplate.queryForObject(
                "select last_seq from im_conversation where conversation_id = ? for update",
                Long.class,
                conversationId
        );
        if (v == null) {
            throw new IllegalArgumentException("conversation not found: " + conversationId);
        }
        return v;
    }

    @Override
    public void updateLastSeq(String conversationId, long lastSeq) {
        jdbcTemplate.update(
                "update im_conversation set last_seq = ?, updated_at = current_timestamp where conversation_id = ?",
                lastSeq,
                conversationId
        );
    }
}
