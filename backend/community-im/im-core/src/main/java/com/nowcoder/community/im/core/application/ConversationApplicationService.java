package com.nowcoder.community.im.core.application;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.exception.CommonErrorCode;
import com.nowcoder.community.im.core.application.result.ConversationResults;
import com.nowcoder.community.im.core.domain.repository.ConversationReadStateRepository;
import com.nowcoder.community.im.core.domain.repository.ConversationRepository;
import com.nowcoder.community.im.core.domain.repository.PrivateMessageRepository;
import com.nowcoder.community.im.core.domain.repository.UserInboxRepository;
import com.nowcoder.community.im.core.support.ConversationIdSupport;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class ConversationApplicationService {

    private final PrivateMessageRepository privateMessageRepository;
    private final ConversationReadStateRepository readStateRepository;
    private final ConversationRepository conversationRepository;
    private final UserInboxRepository userInboxRepository;

    public ConversationApplicationService(
            PrivateMessageRepository privateMessageRepository,
            ConversationReadStateRepository readStateRepository,
            ConversationRepository conversationRepository,
            UserInboxRepository userInboxRepository
    ) {
        this.privateMessageRepository = privateMessageRepository;
        this.readStateRepository = readStateRepository;
        this.conversationRepository = conversationRepository;
        this.userInboxRepository = userInboxRepository;
    }

    @Transactional(readOnly = true)
    public List<ConversationResults.ListItem> listConversations(UUID viewerId, int page, int size) {
        int s = Math.min(Math.max(1, size), 200);
        int p = Math.max(0, page);
        long offset = Math.multiplyExact((long) p, (long) s);
        return userInboxRepository.listConversations(viewerId, s, offset).stream()
                .map(item -> new ConversationResults.ListItem(
                        item.conversationId(),
                        item.otherUserId(),
                        item.lastSeq(),
                        item.lastReadSeq(),
                        item.unreadCount(),
                        item.lastMessage() == null ? null : new ConversationResults.LastMessage(
                                item.lastMessage().messageId(),
                                item.lastMessage().fromUserId(),
                                item.lastMessage().toUserId(),
                                item.lastMessage().content(),
                                item.lastMessage().createdAt() == null ? 0L : item.lastMessage().createdAt().toEpochMilli()
                        )
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public ConversationResults.Messages listMessages(
            UUID viewerId,
            String conversationId,
            long afterSeq,
            int limit
    ) {
        ParsedConversationId parsed = parseConversationId(conversationId);
        if (parsed == null) {
            throw new IllegalArgumentException("invalid conversationId");
        }
        assertConversationMember(viewerId, parsed);

        int l = Math.min(Math.max(1, limit), 200);
        long after = Math.max(0L, afterSeq);
        String canonicalConversationId = parsed.canonicalConversationId;
        if (!conversationRepository.exists(canonicalConversationId)) {
            return new ConversationResults.Messages(canonicalConversationId, List.of(), after, 0L);
        }

        List<ConversationResults.MessageItem> items = privateMessageRepository.listAfterSeq(canonicalConversationId, after, l)
                .stream()
                .map(r -> new ConversationResults.MessageItem(
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
        long lastReadSeq = readStateRepository.getLastReadSeq(canonicalConversationId, viewerId);
        return new ConversationResults.Messages(canonicalConversationId, items, nextAfterSeq, lastReadSeq);
    }

    @Transactional
    public void markRead(UUID viewerId, String conversationId, long requestedLastReadSeq) {
        ParsedConversationId parsed = parseConversationId(conversationId);
        if (parsed == null) {
            throw new IllegalArgumentException("invalid conversationId");
        }
        assertConversationMember(viewerId, parsed);

        String canonicalConversationId = parsed.canonicalConversationId;
        if (!conversationRepository.exists(canonicalConversationId)) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND);
        }

        long lastReadSeq = Math.max(0L, requestedLastReadSeq);
        if (lastReadSeq > 0) {
            readStateRepository.updateLastReadSeqMax(canonicalConversationId, viewerId, lastReadSeq);
            userInboxRepository.markConversationRead(canonicalConversationId, viewerId, lastReadSeq);
        }
    }

    private static void assertConversationMember(UUID viewerId, ParsedConversationId parsed) {
        if (!viewerId.equals(parsed.user1) && !viewerId.equals(parsed.user2)) {
            throw new AccessDeniedException("not a conversation member");
        }
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
}
