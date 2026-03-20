package com.nowcoder.community.im.core.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UnreadService {

    private final JdbcTemplate jdbcTemplate;

    public UnreadService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<RoomUnreadItem> listRoomUnread(int userId, int limit) {
        int l = Math.min(Math.max(1, limit), 5000);
        return jdbcTemplate.query(
                "select m.room_id, r.last_seq, coalesce(s.last_read_seq, 0) as last_read_seq " +
                        "from im_room_member m " +
                        "join im_room r on r.room_id = m.room_id " +
                        "left join im_room_read_state s on s.room_id = m.room_id and s.user_id = m.user_id " +
                        "where m.user_id = ? " +
                        "order by m.room_id asc " +
                        "limit ?",
                (rs, rowNum) -> {
                    long roomId = rs.getLong("room_id");
                    long lastSeq = rs.getLong("last_seq");
                    long lastReadSeq = rs.getLong("last_read_seq");
                    long unread = Math.max(0L, lastSeq - lastReadSeq);
                    return new RoomUnreadItem(roomId, lastSeq, lastReadSeq, unread);
                },
                userId,
                l
        );
    }

    public List<ConversationUnreadItem> listConversationUnread(int userId, int limit) {
        int l = Math.min(Math.max(1, limit), 5000);
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
                userId,
                userId,
                userId,
                l
        );
    }

    public record RoomUnreadItem(long roomId, long lastSeq, long lastReadSeq, long unreadCount) {
    }

    public record ConversationUnreadItem(String conversationId, long lastSeq, long lastReadSeq, long unreadCount) {
    }
}

