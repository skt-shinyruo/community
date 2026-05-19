package com.nowcoder.community.im.core.infrastructure.persistence;

import com.nowcoder.community.im.core.domain.model.ConversationUnreadItem;
import com.nowcoder.community.im.core.domain.model.RoomUnreadItem;
import com.nowcoder.community.im.core.domain.repository.UnreadRepository;
import com.nowcoder.community.im.core.domain.repository.UserInboxRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public class JdbcUnreadRepository implements UnreadRepository {

    private final UserInboxRepository userInboxRepository;

    public JdbcUnreadRepository(UserInboxRepository userInboxRepository) {
        this.userInboxRepository = userInboxRepository;
    }

    @Override
    public List<RoomUnreadItem> listRoomUnread(UUID userId, int limit) {
        return userInboxRepository.listRoomUnread(userId, limit);
    }

    @Override
    public List<ConversationUnreadItem> listConversationUnread(UUID userId, int limit) {
        return userInboxRepository.listConversationUnread(userId, limit);
    }
}
