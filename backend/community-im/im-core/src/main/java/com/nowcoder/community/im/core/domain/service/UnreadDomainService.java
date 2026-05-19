package com.nowcoder.community.im.core.domain.service;

import com.nowcoder.community.im.core.domain.model.ConversationUnreadItem;
import com.nowcoder.community.im.core.domain.model.RoomUnreadItem;
import com.nowcoder.community.im.core.domain.repository.UnreadRepository;

import java.util.List;
import java.util.UUID;

public class UnreadDomainService {

    private final UnreadRepository unreadRepository;

    public UnreadDomainService(UnreadRepository unreadRepository) {
        this.unreadRepository = unreadRepository;
    }

    public List<RoomUnreadItem> listRoomUnread(UUID userId, int limit) {
        int l = Math.min(Math.max(1, limit), 5000);
        return unreadRepository.listRoomUnread(userId, l);
    }

    public List<ConversationUnreadItem> listConversationUnread(UUID userId, int limit) {
        int l = Math.min(Math.max(1, limit), 5000);
        return unreadRepository.listConversationUnread(userId, l);
    }
}
