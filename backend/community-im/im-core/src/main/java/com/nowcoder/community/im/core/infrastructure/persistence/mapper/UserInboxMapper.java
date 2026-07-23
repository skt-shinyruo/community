package com.nowcoder.community.im.core.infrastructure.persistence.mapper;

import com.nowcoder.community.im.core.infrastructure.persistence.dataobject.ConversationInboxDataObject;
import com.nowcoder.community.im.core.infrastructure.persistence.dataobject.ConversationUnreadDataObject;
import com.nowcoder.community.im.core.infrastructure.persistence.dataobject.RoomLastMessageDataObject;
import com.nowcoder.community.im.core.infrastructure.persistence.dataobject.RoomUnreadDataObject;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
@Mapper
public interface UserInboxMapper {

    int insertMissingRoomInboxRows(
            @Param("roomId") UUID roomId,
            @Param("fromUserId") UUID fromUserId,
            @Param("seq") long seq,
            @Param("messageId") UUID messageId,
            @Param("content") String content,
            @Param("createdAt") Instant createdAt
    );

    int applyRoomMessage(
            @Param("roomId") UUID roomId,
            @Param("fromUserId") UUID fromUserId,
            @Param("seq") long seq,
            @Param("messageId") UUID messageId,
            @Param("content") String content,
            @Param("createdAt") Instant createdAt
    );

    Long selectRoomLastSeq(@Param("roomId") UUID roomId);

    RoomLastMessageDataObject selectRoomLastMessage(@Param("roomId") UUID roomId, @Param("seq") long seq);

    Long selectRoomReadSeq(@Param("roomId") UUID roomId, @Param("userId") UUID userId);

    int insertRoomInbox(
            @Param("userId") UUID userId,
            @Param("roomId") UUID roomId,
            @Param("lastSeq") long lastSeq,
            @Param("lastMessageId") UUID lastMessageId,
            @Param("lastFromUserId") UUID lastFromUserId,
            @Param("lastContent") String lastContent,
            @Param("lastMessageCreatedAt") Instant lastMessageCreatedAt,
            @Param("lastReadSeq") long lastReadSeq,
            @Param("unreadCount") long unreadCount,
            @Param("sortAt") Instant sortAt
    );

    int refreshRoomInboxOnDuplicate(
            @Param("userId") UUID userId,
            @Param("roomId") UUID roomId,
            @Param("lastSeq") long lastSeq,
            @Param("lastReadSeq") long lastReadSeq
    );

    int deleteRoomInbox(@Param("roomId") UUID roomId, @Param("userId") UUID userId);

    int markConversationRead(
            @Param("conversationId") String conversationId,
            @Param("userId") UUID userId,
            @Param("lastReadSeq") long lastReadSeq
    );

    int markRoomRead(
            @Param("roomId") UUID roomId,
            @Param("userId") UUID userId,
            @Param("lastReadSeq") long lastReadSeq
    );

    List<ConversationInboxDataObject> selectConversations(
            @Param("userId") UUID userId,
            @Param("limit") int limit,
            @Param("offset") long offset
    );

    List<ConversationInboxDataObject> selectConversationsBefore(
            @Param("userId") UUID userId,
            @Param("beforeSortAt") Instant beforeSortAt,
            @Param("afterConversationId") String afterConversationId,
            @Param("limit") int limit
    );

    List<RoomUnreadDataObject> selectRoomUnread(@Param("userId") UUID userId, @Param("limit") int limit);

    List<ConversationUnreadDataObject> selectConversationUnread(@Param("userId") UUID userId, @Param("limit") int limit);

    Long selectConversationReadSeq(@Param("conversationId") String conversationId, @Param("userId") UUID userId);

    int insertConversationInbox(
            @Param("userId") UUID userId,
            @Param("peerUserId") UUID peerUserId,
            @Param("conversationId") String conversationId,
            @Param("seq") long seq,
            @Param("messageId") UUID messageId,
            @Param("fromUserId") UUID fromUserId,
            @Param("toUserId") UUID toUserId,
            @Param("content") String content,
            @Param("createdAt") Instant createdAt,
            @Param("lastReadSeq") long lastReadSeq,
            @Param("unreadCount") long unreadCount
    );

    int updateConversationInbox(
            @Param("userId") UUID userId,
            @Param("peerUserId") UUID peerUserId,
            @Param("conversationId") String conversationId,
            @Param("seq") long seq,
            @Param("messageId") UUID messageId,
            @Param("fromUserId") UUID fromUserId,
            @Param("toUserId") UUID toUserId,
            @Param("content") String content,
            @Param("createdAt") Instant createdAt,
            @Param("lastReadSeq") long lastReadSeq,
            @Param("senderRow") boolean senderRow
    );
}
