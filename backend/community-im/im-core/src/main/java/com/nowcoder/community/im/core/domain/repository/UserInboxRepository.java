package com.nowcoder.community.im.core.domain.repository;

import com.nowcoder.community.im.core.domain.model.ConversationListItem;
import com.nowcoder.community.im.core.domain.model.ConversationUnreadItem;
import com.nowcoder.community.im.core.domain.model.PrivateMessageRecord;
import com.nowcoder.community.im.core.domain.model.RoomMessageRecord;
import com.nowcoder.community.im.core.domain.model.RoomUnreadItem;

import java.util.List;
import java.util.UUID;

public interface UserInboxRepository {

    void applyPrivateMessage(PrivateMessageRecord message);

    void applyRoomMessage(RoomMessageRecord message);

    void ensureRoomMemberInbox(UUID roomId, UUID userId);

    void removeRoomMemberInbox(UUID roomId, UUID userId);

    void markConversationRead(String conversationId, UUID userId, long lastReadSeq);

    void markRoomRead(UUID roomId, UUID userId, long lastReadSeq);

    List<ConversationListItem> listConversations(UUID userId, int limit, long offset);

    List<RoomUnreadItem> listRoomUnread(UUID userId, int limit);

    List<ConversationUnreadItem> listConversationUnread(UUID userId, int limit);
}
