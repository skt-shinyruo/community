package com.nowcoder.community.im.core.infrastructure.persistence;

import com.nowcoder.community.common.id.BinaryUuidCodec;
import com.nowcoder.community.im.core.domain.model.ConversationListItem;
import com.nowcoder.community.im.core.domain.model.ConversationUnreadItem;
import com.nowcoder.community.im.core.domain.model.PrivateMessageRecord;
import com.nowcoder.community.im.core.domain.model.RoomMessageRecord;
import com.nowcoder.community.im.core.domain.model.RoomUnreadItem;
import com.nowcoder.community.im.core.domain.repository.UserInboxRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;

@Repository
public class JdbcUserInboxRepository implements UserInboxRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcUserInboxRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void applyPrivateMessage(PrivateMessageRecord message) {
        upsertConversationInbox(message.fromUserId(), message.toUserId(), message, true);
        upsertConversationInbox(message.toUserId(), message.fromUserId(), message, false);
    }

    @Override
    public void applyRoomMessage(RoomMessageRecord message) {
        insertMissingRoomInboxRows(message);
        jdbcTemplate.update(
                """
                        update im_user_room_inbox
                        set unread_count = case
                              when user_id = ? then 0
                              else greatest(0, unread_count + (? - last_seq))
                            end,
                            last_read_seq = case when user_id = ? then greatest(last_read_seq, ?) else last_read_seq end,
                            last_seq = ?,
                            last_message_id = ?,
                            last_from_user_id = ?,
                            last_content = ?,
                            last_message_created_at = ?,
                            sort_at = ?,
                            updated_at = current_timestamp
                        where room_id = ? and ? > last_seq
                        """,
                BinaryUuidCodec.toBytes(message.fromUserId()),
                message.seq(),
                BinaryUuidCodec.toBytes(message.fromUserId()),
                message.seq(),
                message.seq(),
                BinaryUuidCodec.toBytes(message.messageId()),
                BinaryUuidCodec.toBytes(message.fromUserId()),
                message.content(),
                Timestamp.from(message.createdAt()),
                Timestamp.from(message.createdAt()),
                BinaryUuidCodec.toBytes(message.roomId()),
                message.seq()
        );
    }

    private void insertMissingRoomInboxRows(RoomMessageRecord message) {
        jdbcTemplate.update(
                """
                        insert into im_user_room_inbox(
                          user_id, room_id, last_seq, last_message_id, last_from_user_id, last_content,
                          last_message_created_at, last_read_seq, unread_count, sort_at
                        )
                        select m.user_id,
                               m.room_id,
                               ?,
                               ?,
                               ?,
                               ?,
                               ?,
                               case when m.user_id = ? then ? else coalesce(s.last_read_seq, 0) end,
                               case when m.user_id = ? then 0 else greatest(0, ? - coalesce(s.last_read_seq, 0)) end,
                               ?
                        from im_room_member m
                        left join im_room_read_state s on s.room_id = m.room_id and s.user_id = m.user_id
                        where m.room_id = ?
                          and not exists (
                            select 1
                            from im_user_room_inbox i
                            where i.user_id = m.user_id and i.room_id = m.room_id
                          )
                        """,
                message.seq(),
                BinaryUuidCodec.toBytes(message.messageId()),
                BinaryUuidCodec.toBytes(message.fromUserId()),
                message.content(),
                Timestamp.from(message.createdAt()),
                BinaryUuidCodec.toBytes(message.fromUserId()),
                message.seq(),
                BinaryUuidCodec.toBytes(message.fromUserId()),
                message.seq(),
                Timestamp.from(message.createdAt()),
                BinaryUuidCodec.toBytes(message.roomId())
        );
    }

    @Override
    public void ensureRoomMemberInbox(UUID roomId, UUID userId) {
        Long lastSeq = jdbcTemplate.query(
                "select last_seq from im_room where room_id = ?",
                rs -> rs.next() ? rs.getLong("last_seq") : null,
                BinaryUuidCodec.toBytes(roomId)
        );
        if (lastSeq == null) {
            throw new IllegalArgumentException("room not found: " + roomId);
        }
        RoomLastMessage lastMessage = findRoomLastMessage(roomId, lastSeq);
        long lastReadSeq = readRoomReadSeq(roomId, userId);
        try {
            insertRoomInbox(userId, roomId, lastSeq, lastMessage, lastReadSeq);
        } catch (DuplicateKeyException ignore) {
            jdbcTemplate.update(
                    """
                            update im_user_room_inbox
                            set last_seq = greatest(last_seq, ?),
                                last_read_seq = greatest(last_read_seq, ?),
                                unread_count = greatest(0, greatest(last_seq, ?) - greatest(last_read_seq, ?)),
                                updated_at = current_timestamp
                            where user_id = ? and room_id = ?
                            """,
                    lastSeq,
                    lastReadSeq,
                    lastSeq,
                    lastReadSeq,
                    BinaryUuidCodec.toBytes(userId),
                    BinaryUuidCodec.toBytes(roomId)
            );
        }
    }

    @Override
    public void removeRoomMemberInbox(UUID roomId, UUID userId) {
        jdbcTemplate.update(
                "delete from im_user_room_inbox where user_id = ? and room_id = ?",
                BinaryUuidCodec.toBytes(userId),
                BinaryUuidCodec.toBytes(roomId)
        );
    }

    @Override
    public void markConversationRead(String conversationId, UUID userId, long lastReadSeq) {
        jdbcTemplate.update(
                """
                        update im_user_conversation_inbox
                        set last_read_seq = greatest(last_read_seq, ?),
                            unread_count = greatest(0, last_seq - greatest(last_read_seq, ?)),
                            updated_at = current_timestamp
                        where user_id = ? and conversation_id = ?
                        """,
                lastReadSeq,
                lastReadSeq,
                BinaryUuidCodec.toBytes(userId),
                conversationId
        );
    }

    @Override
    public void markRoomRead(UUID roomId, UUID userId, long lastReadSeq) {
        jdbcTemplate.update(
                """
                        update im_user_room_inbox
                        set last_read_seq = greatest(last_read_seq, ?),
                            unread_count = greatest(0, last_seq - greatest(last_read_seq, ?)),
                            updated_at = current_timestamp
                        where user_id = ? and room_id = ?
                        """,
                lastReadSeq,
                lastReadSeq,
                BinaryUuidCodec.toBytes(userId),
                BinaryUuidCodec.toBytes(roomId)
        );
    }

    @Override
    public List<ConversationListItem> listConversations(UUID userId, int limit, long offset) {
        List<ConversationListItem> items = jdbcTemplate.query(
                """
                        select conversation_id, peer_user_id, last_seq, last_read_seq, unread_count,
                               last_message_id, last_from_user_id, last_to_user_id, last_content, last_message_created_at
                        from im_user_conversation_inbox
                        where user_id = ?
                        order by sort_at desc, conversation_id asc
                        limit ? offset ?
                        """,
                (rs, rowNum) -> {
                    UUID lastMessageId = BinaryUuidCodec.fromBytes(rs.getBytes("last_message_id"));
                    Timestamp lastMessageCreatedAt = rs.getTimestamp("last_message_created_at");
                    ConversationListItem.LastMessage lastMessage = lastMessageId == null ? null : new ConversationListItem.LastMessage(
                            lastMessageId,
                            BinaryUuidCodec.fromBytes(rs.getBytes("last_from_user_id")),
                            BinaryUuidCodec.fromBytes(rs.getBytes("last_to_user_id")),
                            rs.getString("last_content"),
                            lastMessageCreatedAt == null ? null : lastMessageCreatedAt.toInstant()
                    );
                    return new ConversationListItem(
                            rs.getString("conversation_id"),
                            BinaryUuidCodec.fromBytes(rs.getBytes("peer_user_id")),
                            rs.getLong("last_seq"),
                            rs.getLong("last_read_seq"),
                            rs.getLong("unread_count"),
                            lastMessage
                    );
                },
                BinaryUuidCodec.toBytes(userId),
                limit,
                offset
        );
        return items == null ? List.of() : items;
    }

    @Override
    public List<RoomUnreadItem> listRoomUnread(UUID userId, int limit) {
        return jdbcTemplate.query(
                """
                        select room_id, last_seq, last_read_seq, unread_count
                        from im_user_room_inbox
                        where user_id = ?
                        order by sort_at desc, room_id asc
                        limit ?
                        """,
                (rs, rowNum) -> new RoomUnreadItem(
                        BinaryUuidCodec.fromBytes(rs.getBytes("room_id")),
                        rs.getLong("last_seq"),
                        rs.getLong("last_read_seq"),
                        rs.getLong("unread_count")
                ),
                BinaryUuidCodec.toBytes(userId),
                limit
        );
    }

    @Override
    public List<ConversationUnreadItem> listConversationUnread(UUID userId, int limit) {
        return jdbcTemplate.query(
                """
                        select conversation_id, last_seq, last_read_seq, unread_count
                        from im_user_conversation_inbox
                        where user_id = ?
                        order by sort_at desc, conversation_id asc
                        limit ?
                        """,
                (rs, rowNum) -> new ConversationUnreadItem(
                        rs.getString("conversation_id"),
                        rs.getLong("last_seq"),
                        rs.getLong("last_read_seq"),
                        rs.getLong("unread_count")
                ),
                BinaryUuidCodec.toBytes(userId),
                limit
        );
    }

    private void upsertConversationInbox(
            UUID userId,
            UUID peerUserId,
            PrivateMessageRecord message,
            boolean senderRow
    ) {
        long currentLastReadSeq = readConversationReadSeq(message.conversationId(), userId);
        long lastReadSeq = senderRow ? Math.max(currentLastReadSeq, message.seq()) : currentLastReadSeq;
        try {
            insertConversationInbox(userId, peerUserId, message, lastReadSeq, Math.max(0L, message.seq() - lastReadSeq));
        } catch (DuplicateKeyException ignore) {
            int isSender = senderRow ? 1 : 0;
            jdbcTemplate.update(
                    """
                            update im_user_conversation_inbox
                            set peer_user_id = ?,
                                last_seq = greatest(last_seq, ?),
                                last_message_id = case when ? >= last_seq then ? else last_message_id end,
                                last_from_user_id = case when ? >= last_seq then ? else last_from_user_id end,
                                last_to_user_id = case when ? >= last_seq then ? else last_to_user_id end,
                                last_content = case when ? >= last_seq then ? else last_content end,
                                last_message_created_at = case when ? >= last_seq then ? else last_message_created_at end,
                                last_read_seq = greatest(last_read_seq, ?),
                                unread_count = case
                                  when ? <= last_seq then unread_count
                                  when ? = 1 then 0
                                  else greatest(0, unread_count + (? - last_seq))
                                end,
                                sort_at = case when ? >= last_seq then ? else sort_at end,
                                updated_at = current_timestamp
                            where user_id = ? and conversation_id = ?
                            """,
                    BinaryUuidCodec.toBytes(peerUserId),
                    message.seq(),
                    message.seq(),
                    BinaryUuidCodec.toBytes(message.messageId()),
                    message.seq(),
                    BinaryUuidCodec.toBytes(message.fromUserId()),
                    message.seq(),
                    BinaryUuidCodec.toBytes(message.toUserId()),
                    message.seq(),
                    message.content(),
                    message.seq(),
                    Timestamp.from(message.createdAt()),
                    lastReadSeq,
                    message.seq(),
                    isSender,
                    message.seq(),
                    message.seq(),
                    Timestamp.from(message.createdAt()),
                    BinaryUuidCodec.toBytes(userId),
                    message.conversationId()
            );
        }
    }

    private void insertConversationInbox(
            UUID userId,
            UUID peerUserId,
            PrivateMessageRecord message,
            long lastReadSeq,
            long unreadCount
    ) {
        jdbcTemplate.update(
                """
                        insert into im_user_conversation_inbox(
                          user_id, conversation_id, peer_user_id, last_seq,
                          last_message_id, last_from_user_id, last_to_user_id, last_content, last_message_created_at,
                          last_read_seq, unread_count, sort_at
                        )
                        values (?,?,?,?,?,?,?,?,?,?,?,?)
                        """,
                BinaryUuidCodec.toBytes(userId),
                message.conversationId(),
                BinaryUuidCodec.toBytes(peerUserId),
                message.seq(),
                BinaryUuidCodec.toBytes(message.messageId()),
                BinaryUuidCodec.toBytes(message.fromUserId()),
                BinaryUuidCodec.toBytes(message.toUserId()),
                message.content(),
                Timestamp.from(message.createdAt()),
                lastReadSeq,
                unreadCount,
                Timestamp.from(message.createdAt())
        );
    }

    private void insertRoomInbox(UUID userId, UUID roomId, long lastSeq, RoomLastMessage lastMessage, long lastReadSeq) {
        jdbcTemplate.update(
                """
                        insert into im_user_room_inbox(
                          user_id, room_id, last_seq, last_message_id, last_from_user_id, last_content,
                          last_message_created_at, last_read_seq, unread_count, sort_at
                        )
                        values (?,?,?,?,?,?,?,?,?,?)
                        """,
                BinaryUuidCodec.toBytes(userId),
                BinaryUuidCodec.toBytes(roomId),
                lastSeq,
                lastMessage == null ? null : BinaryUuidCodec.toBytes(lastMessage.messageId()),
                lastMessage == null ? null : BinaryUuidCodec.toBytes(lastMessage.fromUserId()),
                lastMessage == null ? null : lastMessage.content(),
                lastMessage == null ? null : lastMessage.createdAt(),
                lastReadSeq,
                Math.max(0L, lastSeq - lastReadSeq),
                lastMessage == null ? new Timestamp(System.currentTimeMillis()) : lastMessage.createdAt()
        );
    }

    private RoomLastMessage findRoomLastMessage(UUID roomId, long lastSeq) {
        if (lastSeq <= 0) {
            return null;
        }
        List<RoomLastMessage> rows = jdbcTemplate.query(
                """
                        select message_id, from_user_id, content, created_at
                        from im_room_message
                        where room_id = ? and seq = ?
                        """,
                (rs, rowNum) -> new RoomLastMessage(
                        BinaryUuidCodec.fromBytes(rs.getBytes("message_id")),
                        BinaryUuidCodec.fromBytes(rs.getBytes("from_user_id")),
                        rs.getString("content"),
                        rs.getTimestamp("created_at")
                ),
                BinaryUuidCodec.toBytes(roomId),
                lastSeq
        );
        return rows.isEmpty() ? null : rows.get(0);
    }

    private long readConversationReadSeq(String conversationId, UUID userId) {
        Long v = jdbcTemplate.query(
                "select last_read_seq from im_conversation_read_state where conversation_id = ? and user_id = ?",
                rs -> rs.next() ? rs.getLong(1) : null,
                conversationId,
                BinaryUuidCodec.toBytes(userId)
        );
        return v == null ? 0L : v;
    }

    private long readRoomReadSeq(UUID roomId, UUID userId) {
        Long v = jdbcTemplate.query(
                "select last_read_seq from im_room_read_state where room_id = ? and user_id = ?",
                rs -> rs.next() ? rs.getLong(1) : null,
                BinaryUuidCodec.toBytes(roomId),
                BinaryUuidCodec.toBytes(userId)
        );
        return v == null ? 0L : v;
    }

    private record RoomLastMessage(UUID messageId, UUID fromUserId, String content, Timestamp createdAt) {
    }
}
