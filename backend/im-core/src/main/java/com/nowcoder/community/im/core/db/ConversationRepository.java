package com.nowcoder.community.im.core.db;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ConversationRepository {

    private final JdbcTemplate jdbcTemplate;

    public ConversationRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean exists(String conversationId) {
        Integer n = jdbcTemplate.queryForObject(
                "select count(1) from im_conversation where conversation_id = ?",
                Integer.class,
                conversationId
        );
        return n != null && n > 0;
    }

    public void ensureExists(String conversationId, int userA, int userB) {
        try {
            jdbcTemplate.update(
                    "insert into im_conversation(conversation_id, user_a, user_b, last_seq) values (?,?,?,0)",
                    conversationId,
                    userA,
                    userB
            );
        } catch (DuplicateKeyException ignore) {
            // idempotent: already exists
        }
    }

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

    public void updateLastSeq(String conversationId, long lastSeq) {
        jdbcTemplate.update(
                "update im_conversation set last_seq = ?, updated_at = current_timestamp where conversation_id = ?",
                lastSeq,
                conversationId
        );
    }
}

