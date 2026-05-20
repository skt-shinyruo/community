package com.nowcoder.community.im.core.infrastructure.persistence;

import com.nowcoder.community.im.core.domain.model.ConversationListItem;
import com.nowcoder.community.im.core.domain.model.ConversationUnreadItem;
import com.nowcoder.community.im.core.domain.model.PrivateMessageRecord;
import com.nowcoder.community.im.core.domain.model.RoomMessageRecord;
import com.nowcoder.community.im.core.domain.model.RoomUnreadItem;
import com.nowcoder.community.im.core.domain.repository.UserInboxRepository;
import com.nowcoder.community.im.core.infrastructure.persistence.dataobject.ConversationInboxDataObject;
import com.nowcoder.community.im.core.infrastructure.persistence.dataobject.ConversationUnreadDataObject;
import com.nowcoder.community.im.core.infrastructure.persistence.dataobject.RoomLastMessageDataObject;
import com.nowcoder.community.im.core.infrastructure.persistence.dataobject.RoomUnreadDataObject;
import com.nowcoder.community.im.core.infrastructure.persistence.mapper.UserInboxMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public class MyBatisUserInboxRepository implements UserInboxRepository {

    private final UserInboxMapper mapper;

    public MyBatisUserInboxRepository(UserInboxMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void applyPrivateMessage(PrivateMessageRecord message) {
        upsertConversationInbox(message.fromUserId(), message.toUserId(), message, true);
        upsertConversationInbox(message.toUserId(), message.fromUserId(), message, false);
    }

    @Override
    public void applyRoomMessage(RoomMessageRecord message) {
        insertMissingRoomInboxRows(message);
        mapper.applyRoomMessage(
                message.roomId(),
                message.fromUserId(),
                message.seq(),
                message.messageId(),
                message.content(),
                message.createdAt()
        );
    }

    private void insertMissingRoomInboxRows(RoomMessageRecord message) {
        mapper.insertMissingRoomInboxRows(
                message.roomId(),
                message.fromUserId(),
                message.seq(),
                message.messageId(),
                message.content(),
                message.createdAt()
        );
    }

    @Override
    public void ensureRoomMemberInbox(UUID roomId, UUID userId) {
        Long lastSeq = mapper.selectRoomLastSeq(roomId);
        if (lastSeq == null) {
            throw new IllegalArgumentException("room not found: " + roomId);
        }
        RoomLastMessageDataObject lastMessage = findRoomLastMessage(roomId, lastSeq);
        long lastReadSeq = readRoomReadSeq(roomId, userId);
        try {
            insertRoomInbox(userId, roomId, lastSeq, lastMessage, lastReadSeq);
        } catch (DuplicateKeyException ignore) {
            mapper.refreshRoomInboxOnDuplicate(userId, roomId, lastSeq, lastReadSeq);
        }
    }

    @Override
    public void removeRoomMemberInbox(UUID roomId, UUID userId) {
        mapper.deleteRoomInbox(roomId, userId);
    }

    @Override
    public void markConversationRead(String conversationId, UUID userId, long lastReadSeq) {
        mapper.markConversationRead(conversationId, userId, lastReadSeq);
    }

    @Override
    public void markRoomRead(UUID roomId, UUID userId, long lastReadSeq) {
        mapper.markRoomRead(roomId, userId, lastReadSeq);
    }

    @Override
    public List<ConversationListItem> listConversations(UUID userId, int limit, long offset) {
        List<ConversationInboxDataObject> rows = mapper.selectConversations(userId, limit, offset);
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        return rows.stream()
                .map(ConversationInboxDataObject::toListItem)
                .toList();
    }

    @Override
    public List<RoomUnreadItem> listRoomUnread(UUID userId, int limit) {
        return mapper.selectRoomUnread(userId, limit).stream()
                .map(RoomUnreadDataObject::toDomain)
                .toList();
    }

    @Override
    public List<ConversationUnreadItem> listConversationUnread(UUID userId, int limit) {
        return mapper.selectConversationUnread(userId, limit).stream()
                .map(ConversationUnreadDataObject::toDomain)
                .toList();
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
            mapper.updateConversationInbox(
                    userId,
                    peerUserId,
                    message.conversationId(),
                    message.seq(),
                    message.messageId(),
                    message.fromUserId(),
                    message.toUserId(),
                    message.content(),
                    message.createdAt(),
                    lastReadSeq,
                    senderRow
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
        mapper.insertConversationInbox(
                userId,
                peerUserId,
                message.conversationId(),
                message.seq(),
                message.messageId(),
                message.fromUserId(),
                message.toUserId(),
                message.content(),
                message.createdAt(),
                lastReadSeq,
                unreadCount
        );
    }

    private void insertRoomInbox(UUID userId, UUID roomId, long lastSeq, RoomLastMessageDataObject lastMessage, long lastReadSeq) {
        Instant sortAt = lastMessage == null || lastMessage.getCreatedAt() == null ? Instant.now() : lastMessage.getCreatedAt();
        mapper.insertRoomInbox(
                userId,
                roomId,
                lastSeq,
                lastMessage == null ? null : lastMessage.getMessageId(),
                lastMessage == null ? null : lastMessage.getFromUserId(),
                lastMessage == null ? null : lastMessage.getContent(),
                lastMessage == null ? null : lastMessage.getCreatedAt(),
                lastReadSeq,
                Math.max(0L, lastSeq - lastReadSeq),
                sortAt
        );
    }

    private RoomLastMessageDataObject findRoomLastMessage(UUID roomId, long lastSeq) {
        if (lastSeq <= 0) {
            return null;
        }
        return mapper.selectRoomLastMessage(roomId, lastSeq);
    }

    private long readConversationReadSeq(String conversationId, UUID userId) {
        Long v = mapper.selectConversationReadSeq(conversationId, userId);
        return v == null ? 0L : v;
    }

    private long readRoomReadSeq(UUID roomId, UUID userId) {
        Long v = mapper.selectRoomReadSeq(roomId, userId);
        return v == null ? 0L : v;
    }
}
