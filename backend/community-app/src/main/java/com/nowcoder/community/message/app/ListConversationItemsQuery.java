package com.nowcoder.community.message.app;

import com.nowcoder.community.infra.security.auth.CurrentUser;
import com.nowcoder.community.message.dto.ConversationItemResponse;
import com.nowcoder.community.message.dto.LetterItemResponse;
import com.nowcoder.community.message.service.PrivateMessageService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ListConversationItemsQuery {

    private final PrivateMessageService privateMessageService;

    public ListConversationItemsQuery(PrivateMessageService privateMessageService) {
        this.privateMessageService = privateMessageService;
    }

    public List<LetterItemResponse> listConversations(Authentication authentication, Integer page, Integer size) {
        int userId = CurrentUser.requireUserId(authentication);
        return privateMessageService.listConversationSummaries(userId, normalizePage(page), normalizeSize(size));
    }

    public List<ConversationItemResponse> listConversationItems(Authentication authentication, Integer page, Integer size) {
        int userId = CurrentUser.requireUserId(authentication);
        return privateMessageService.listConversationItems(userId, normalizePage(page), normalizeSize(size));
    }

    private int normalizePage(Integer page) {
        return page == null ? 0 : Math.max(0, page);
    }

    private int normalizeSize(Integer size) {
        return size == null ? 10 : Math.min(50, Math.max(1, size));
    }
}
