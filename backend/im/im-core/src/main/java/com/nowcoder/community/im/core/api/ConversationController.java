package com.nowcoder.community.im.core.api;

import com.nowcoder.community.im.core.db.ConversationReadStateRepository;
import com.nowcoder.community.im.core.db.PrivateMessageRepository;
import com.nowcoder.community.im.core.security.CurrentUser;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.sql.Timestamp;
import java.util.List;

@RestController
@RequestMapping("/api/im/conversations")
public class ConversationController {

    private final PrivateMessageRepository privateMessageRepository;
    private final ConversationReadStateRepository readStateRepository;
    private final JdbcTemplate jdbcTemplate;

    public ConversationController(
            PrivateMessageRepository privateMessageRepository,
            ConversationReadStateRepository readStateRepository,
            JdbcTemplate jdbcTemplate
    ) {
        this.privateMessageRepository = privateMessageRepository;
        this.readStateRepository = readStateRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping
    public List<ConversationListItem> listConversations(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(name = "page", required = false, defaultValue = "0") int page,
            @RequestParam(name = "size", required = false, defaultValue = "20") int size
    ) {
        int me = CurrentUser.userIdOrThrow(jwt);
        int s = Math.min(Math.max(1, size), 200);
        int p = Math.max(0, page);
        int offset = p * s;
        return jdbcTemplate.query(
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
                    int userA = rs.getInt("user_a");
                    int userB = rs.getInt("user_b");
                    int otherUserId = userA == me ? userB : userA;

                    long lastSeq = rs.getLong("last_seq");
                    long lastReadSeq = rs.getLong("last_read_seq");
                    long unread = Math.max(0L, lastSeq - lastReadSeq);

                    long lastMessageId = rs.getLong("last_message_id");
                    LastMessage lastMessage = null;
                    if (!rs.wasNull() && lastMessageId > 0) {
                        int fromUserId = rs.getInt("last_from_user_id");
                        int toUserId = rs.getInt("last_to_user_id");
                        String content = rs.getString("last_content");
                        Timestamp ts = rs.getTimestamp("last_created_at");
                        long createdAtEpochMs = ts == null ? 0L : ts.toInstant().toEpochMilli();
                        lastMessage = new LastMessage(lastMessageId, fromUserId, toUserId, content, createdAtEpochMs);
                    }

                    return new ConversationListItem(conversationId, otherUserId, lastSeq, lastReadSeq, unread, lastMessage);
                },
                me,
                me,
                me,
                s,
                offset
        );
    }

    @GetMapping("/{conversationId}/messages")
    public ConversationMessagesResponse listMessages(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String conversationId,
            @RequestParam(name = "afterSeq", required = false, defaultValue = "0") long afterSeq,
            @RequestParam(name = "limit", required = false, defaultValue = "50") int limit
    ) {
        int me = CurrentUser.userIdOrThrow(jwt);
        if (!conversationContainsUser(conversationId, me)) {
            throw new AccessDeniedException("not a conversation member");
        }

        int l = Math.min(Math.max(1, limit), 200);
        long after = Math.max(0L, afterSeq);
        List<PrivateMessageRepository.PrivateMessageRow> rows = privateMessageRepository.listAfterSeq(conversationId, after, l);
        List<ConversationMessageItem> items = rows.stream()
                .map(r -> new ConversationMessageItem(
                        r.conversationId(),
                        r.seq(),
                        r.messageId(),
                        r.fromUserId(),
                        r.toUserId(),
                        r.content(),
                        r.clientMsgId(),
                        r.createdAt().toEpochMilli()
                ))
                .toList();
        long nextAfterSeq = items.isEmpty() ? after : items.get(items.size() - 1).seq();
        long lastReadSeq = readStateRepository.getLastReadSeq(conversationId, me);
        return new ConversationMessagesResponse(conversationId, items, nextAfterSeq, lastReadSeq);
    }

    @PostMapping("/{conversationId}/read")
    public void markRead(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String conversationId,
            @RequestBody MarkReadRequest req
    ) {
        int me = CurrentUser.userIdOrThrow(jwt);
        if (!conversationContainsUser(conversationId, me)) {
            throw new AccessDeniedException("not a conversation member");
        }
        long lastReadSeq = req == null ? 0L : Math.max(0L, req.lastReadSeq());
        readStateRepository.updateLastReadSeqMax(conversationId, me, lastReadSeq);
    }

    private static boolean conversationContainsUser(String conversationId, int userId) {
        if (conversationId == null || conversationId.isBlank()) {
            return false;
        }
        String[] parts = conversationId.split("_");
        if (parts.length != 2) {
            return false;
        }
        try {
            int a = Integer.parseInt(parts[0].trim());
            int b = Integer.parseInt(parts[1].trim());
            return a == userId || b == userId;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    public record MarkReadRequest(long lastReadSeq) {
    }

    public record ConversationMessagesResponse(
            String conversationId,
            List<ConversationMessageItem> items,
            long nextAfterSeq,
            long lastReadSeq
    ) {
    }

    public record ConversationListItem(
            String conversationId,
            int otherUserId,
            long lastSeq,
            long lastReadSeq,
            long unreadCount,
            LastMessage lastMessage
    ) {
    }

    public record LastMessage(
            long messageId,
            int fromUserId,
            int toUserId,
            String content,
            long createdAtEpochMs
    ) {
    }

    public record ConversationMessageItem(
            String conversationId,
            long seq,
            long messageId,
            int fromUserId,
            int toUserId,
            String content,
            String clientMsgId,
            long createdAtEpochMs
    ) {
    }
}
