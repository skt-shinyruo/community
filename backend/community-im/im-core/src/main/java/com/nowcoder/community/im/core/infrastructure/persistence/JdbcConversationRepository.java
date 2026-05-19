package com.nowcoder.community.im.core.infrastructure.persistence;

import com.nowcoder.community.common.id.BinaryUuidCodec;
import com.nowcoder.community.im.core.domain.model.ConversationListItem;
import com.nowcoder.community.im.core.domain.repository.ConversationRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;
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

    @Override
    public List<ConversationListItem> listByUser(UUID userId, int limit, long offset) {
        byte[] userIdBytes = BinaryUuidCodec.toBytes(userId);
        List<ConversationListItem> items = jdbcTemplate.query(
                "select c.conversation_id, c.user_a, c.user_b, c.last_seq, " +
                        "coalesce(r.last_read_seq, 0) as last_read_seq, " +
                        "m.message_id as last_message_id, m.from_user_id as last_from_user_id, m.to_user_id as last_to_user_id, " +
                        "m.content as last_content, m.created_at as last_created_at " +
                        "from im_conversation c " +
                        "left join im_conversation_read_state r on r.conversation_id = c.conversation_id and r.user_id = ? " +
                        "left join im_private_message m on m.conversation_id = c.conversation_id and m.seq = c.last_seq " +
                        "where c.user_a = ? or c.user_b = ? " +
                        "order by c.updated_at desc " +
                        "limit ? offset ?",
                (rs, rowNum) -> {
                    String conversationId = rs.getString("conversation_id");
                    UUID userA = BinaryUuidCodec.fromBytes(rs.getBytes("user_a"));
                    UUID userB = BinaryUuidCodec.fromBytes(rs.getBytes("user_b"));
                    UUID otherUserId = userId.equals(userA) ? userB : userA;

                    long lastSeq = rs.getLong("last_seq");
                    long lastReadSeq = rs.getLong("last_read_seq");
                    long unread = Math.max(0L, lastSeq - lastReadSeq);

                    UUID lastMessageId = BinaryUuidCodec.fromBytes(rs.getBytes("last_message_id"));
                    ConversationListItem.LastMessage lastMessage = null;
                    if (lastMessageId != null) {
                        Timestamp ts = rs.getTimestamp("last_created_at");
                        lastMessage = new ConversationListItem.LastMessage(
                                lastMessageId,
                                BinaryUuidCodec.fromBytes(rs.getBytes("last_from_user_id")),
                                BinaryUuidCodec.fromBytes(rs.getBytes("last_to_user_id")),
                                rs.getString("last_content"),
                                ts == null ? null : ts.toInstant()
                        );
                    }

                    return new ConversationListItem(
                            conversationId,
                            otherUserId,
                            lastSeq,
                            lastReadSeq,
                            unread,
                            lastMessage
                    );
                },
                userIdBytes,
                userIdBytes,
                userIdBytes,
                limit,
                offset
        );
        return items == null ? List.of() : items;
    }
}
