package com.nowcoder.community.im.core.infrastructure.persistence;

import com.nowcoder.community.common.id.BinaryUuidCodec;
import com.nowcoder.community.im.core.domain.model.ConversationUnreadItem;
import com.nowcoder.community.im.core.domain.model.RoomUnreadItem;
import com.nowcoder.community.im.core.domain.repository.UnreadRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public class JdbcUnreadRepository implements UnreadRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcUnreadRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<RoomUnreadItem> listRoomUnread(UUID userId, int limit) {
        byte[] userIdBytes = BinaryUuidCodec.toBytes(userId);
        return jdbcTemplate.query(
                "select m.room_id, r.last_seq, coalesce(s.last_read_seq, 0) as last_read_seq " +
                        "from im_room_member m " +
                        "join im_room r on r.room_id = m.room_id " +
                        "left join im_room_read_state s on s.room_id = m.room_id and s.user_id = m.user_id " +
                        "where m.user_id = ? " +
                        "order by m.room_id asc " +
                        "limit ?",
                (rs, rowNum) -> {
                    UUID roomId = BinaryUuidCodec.fromBytes(rs.getBytes("room_id"));
                    long lastSeq = rs.getLong("last_seq");
                    long lastReadSeq = rs.getLong("last_read_seq");
                    long unread = Math.max(0L, lastSeq - lastReadSeq);
                    return new RoomUnreadItem(roomId, lastSeq, lastReadSeq, unread);
                },
                userIdBytes,
                limit
        );
    }

    @Override
    public List<ConversationUnreadItem> listConversationUnread(UUID userId, int limit) {
        byte[] userIdBytes = BinaryUuidCodec.toBytes(userId);
        return jdbcTemplate.query(
                "select c.conversation_id, c.last_seq, coalesce(s.last_read_seq, 0) as last_read_seq " +
                        "from im_conversation c " +
                        "left join im_conversation_read_state s on s.conversation_id = c.conversation_id and s.user_id = ? " +
                        "where c.user_a = ? or c.user_b = ? " +
                        "order by c.conversation_id asc " +
                        "limit ?",
                (rs, rowNum) -> {
                    String conversationId = rs.getString("conversation_id");
                    long lastSeq = rs.getLong("last_seq");
                    long lastReadSeq = rs.getLong("last_read_seq");
                    long unread = Math.max(0L, lastSeq - lastReadSeq);
                    return new ConversationUnreadItem(conversationId, lastSeq, lastReadSeq, unread);
                },
                userIdBytes,
                userIdBytes,
                userIdBytes,
                limit
        );
    }
}
