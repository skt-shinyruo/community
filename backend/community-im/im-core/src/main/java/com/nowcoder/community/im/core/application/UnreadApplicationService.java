package com.nowcoder.community.im.core.application;

import com.nowcoder.community.im.core.application.result.UnreadSummaryResult;
import com.nowcoder.community.im.core.domain.repository.UserInboxRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class UnreadApplicationService {

    private final UserInboxRepository userInboxRepository;

    public UnreadApplicationService(UserInboxRepository userInboxRepository) {
        this.userInboxRepository = userInboxRepository;
    }

    @Transactional(readOnly = true)
    public UnreadSummaryResult summary(UUID viewerId, int limit) {
        int l = Math.min(Math.max(1, limit), 5000);
        return new UnreadSummaryResult(
                userInboxRepository.listRoomUnread(viewerId, l).stream()
                        .map(item -> new UnreadSummaryResult.RoomUnreadItem(
                                item.roomId(),
                                item.lastSeq(),
                                item.lastReadSeq(),
                                item.unreadCount()
                        ))
                        .toList(),
                userInboxRepository.listConversationUnread(viewerId, l).stream()
                        .map(item -> new UnreadSummaryResult.ConversationUnreadItem(
                                item.conversationId(),
                                item.lastSeq(),
                                item.lastReadSeq(),
                                item.unreadCount()
                        ))
                        .toList()
        );
    }
}
