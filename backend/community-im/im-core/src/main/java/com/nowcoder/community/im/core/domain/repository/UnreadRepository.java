package com.nowcoder.community.im.core.domain.repository;

import com.nowcoder.community.im.core.domain.model.ConversationUnreadItem;
import com.nowcoder.community.im.core.domain.model.RoomUnreadItem;

import java.util.List;
import java.util.UUID;

public interface UnreadRepository {

    List<RoomUnreadItem> listRoomUnread(UUID userId, int limit);

    List<ConversationUnreadItem> listConversationUnread(UUID userId, int limit);
}
