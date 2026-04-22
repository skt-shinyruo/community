package com.nowcoder.community.im.core.controller;

import com.nowcoder.community.common.id.BinaryUuidCodec;
import com.nowcoder.community.im.core.repository.ConversationReadStateRepository;
import com.nowcoder.community.im.core.repository.ConversationRepository;
import com.nowcoder.community.im.core.repository.PrivateMessageRepository;
import com.nowcoder.community.im.core.security.CurrentUser;
import com.nowcoder.community.im.core.support.ConversationIdSupport;
import com.nowcoder.community.common.web.Result;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.*;

import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/im/conversations")
public class ConversationController {

    private final PrivateMessageRepository privateMessageRepository;
    private final ConversationReadStateRepository readStateRepository;
    private final ConversationRepository conversationRepository;
    private final JdbcTemplate jdbcTemplate;

    public ConversationController(
            PrivateMessageRepository privateMessageRepository,
            ConversationReadStateRepository readStateRepository,
            ConversationRepository conversationRepository,
            JdbcTemplate jdbcTemplate
    ) {
        this.privateMessageRepository = privateMessageRepository;
        this.readStateRepository = readStateRepository;
        this.conversationRepository = conversationRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping
    public Result<List<ConversationListItem>> listConversations(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(name = "page", required = false, defaultValue = "0") int page,
            @RequestParam(name = "size", required = false, defaultValue = "20") int size
    ) {
        UUID me = CurrentUser.userIdOrThrow(jwt);
        byte[] meBytes = BinaryUuidCodec.toBytes(me);
        int s = Math.min(Math.max(1, size), 200);
        int p = Math.max(0, page);
        long offset = (long) p * (long) s;
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
                    UUID otherUserId = me.equals(userA) ? userB : userA;

                    long lastSeq = rs.getLong("last_seq");
                    long lastReadSeq = rs.getLong("last_read_seq");
                    long unread = Math.max(0L, lastSeq - lastReadSeq);

                    UUID lastMessageId = BinaryUuidCodec.fromBytes(rs.getBytes("last_message_id"));
                    LastMessage lastMessage = null;
                    if (lastMessageId != null) {
                        UUID fromUserId = BinaryUuidCodec.fromBytes(rs.getBytes("last_from_user_id"));
                        UUID toUserId = BinaryUuidCodec.fromBytes(rs.getBytes("last_to_user_id"));
                        String content = rs.getString("last_content");
                        Timestamp ts = rs.getTimestamp("last_created_at");
                        long createdAtEpochMs = ts == null ? 0L : ts.toInstant().toEpochMilli();
                        lastMessage = new LastMessage(lastMessageId, fromUserId, toUserId, content, createdAtEpochMs);
                    }

                    return new ConversationListItem(conversationId, otherUserId, lastSeq, lastReadSeq, unread, lastMessage);
                },
                meBytes,
                meBytes,
                meBytes,
                s,
                offset
        );
        return Result.ok(items == null ? List.of() : items);
    }

    @GetMapping("/{conversationId}/messages")
    public Result<ConversationMessagesResponse> listMessages(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String conversationId,
            @RequestParam(name = "afterSeq", required = false, defaultValue = "0") long afterSeq,
            @RequestParam(name = "limit", required = false, defaultValue = "50") int limit
    ) {
        UUID me = CurrentUser.userIdOrThrow(jwt);
        ParsedConversationId parsed = parseConversationId(conversationId);
        if (parsed == null) {
            throw new IllegalArgumentException("invalid conversationId");
        }
        if (!me.equals(parsed.user1) && !me.equals(parsed.user2)) {
            throw new AccessDeniedException("not a conversation member");
        }

        int l = Math.min(Math.max(1, limit), 200);
        long after = Math.max(0L, afterSeq);
        String canonicalConversationId = parsed.canonicalConversationId;
        if (!conversationRepository.exists(canonicalConversationId)) {
            return Result.ok(new ConversationMessagesResponse(canonicalConversationId, List.of(), after, 0L));
        }

        List<PrivateMessageRepository.PrivateMessageRow> rows = privateMessageRepository.listAfterSeq(canonicalConversationId, after, l);
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
        long lastReadSeq = readStateRepository.getLastReadSeq(canonicalConversationId, me);
        return Result.ok(new ConversationMessagesResponse(canonicalConversationId, items, nextAfterSeq, lastReadSeq));
    }

    @PostMapping("/{conversationId}/read")
    public Result<Void> markRead(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String conversationId,
            @RequestBody MarkReadRequest req
    ) {
        UUID me = CurrentUser.userIdOrThrow(jwt);
        ParsedConversationId parsed = parseConversationId(conversationId);
        if (parsed == null) {
            throw new IllegalArgumentException("invalid conversationId");
        }
        if (!me.equals(parsed.user1) && !me.equals(parsed.user2)) {
            throw new AccessDeniedException("not a conversation member");
        }
        String canonicalConversationId = parsed.canonicalConversationId;
        if (!conversationRepository.exists(canonicalConversationId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "会话不存在");
        }

        long lastReadSeq = req == null ? 0L : Math.max(0L, req.lastReadSeq());
        if (lastReadSeq > 0) {
            readStateRepository.updateLastReadSeqMax(canonicalConversationId, me, lastReadSeq);
        }
        return Result.ok();
    }

    private static ParsedConversationId parseConversationId(String conversationId) {
        if (conversationId == null || conversationId.isBlank()) {
            return null;
        }
        String[] parts = conversationId.split("_");
        if (parts.length != 2) {
            return null;
        }
        try {
            UUID a = UUID.fromString(parts[0].trim());
            UUID b = UUID.fromString(parts[1].trim());
            if (a.equals(b)) {
                return null;
            }
            return new ParsedConversationId(a, b, ConversationIdSupport.conversationId(a, b));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private record ParsedConversationId(UUID user1, UUID user2, String canonicalConversationId) {
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
            UUID otherUserId,
            long lastSeq,
            long lastReadSeq,
            long unreadCount,
            LastMessage lastMessage
    ) {
    }

    public record LastMessage(
            UUID messageId,
            UUID fromUserId,
            UUID toUserId,
            String content,
            long createdAtEpochMs
    ) {
    }

    public record ConversationMessageItem(
            String conversationId,
            long seq,
            UUID messageId,
            UUID fromUserId,
            UUID toUserId,
            String content,
            String clientMsgId,
            long createdAtEpochMs
    ) {
    }
}
