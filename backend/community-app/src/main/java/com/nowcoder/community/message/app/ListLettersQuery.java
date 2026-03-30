package com.nowcoder.community.message.app;

import com.nowcoder.community.infra.security.auth.CurrentUser;
import com.nowcoder.community.message.dto.LetterItemResponse;
import com.nowcoder.community.message.service.PrivateMessageService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ListLettersQuery {

    private final PrivateMessageService privateMessageService;

    public ListLettersQuery(PrivateMessageService privateMessageService) {
        this.privateMessageService = privateMessageService;
    }

    public List<LetterItemResponse> listLetters(
            Authentication authentication,
            String conversationId,
            Integer page,
            Integer size
    ) {
        int userId = CurrentUser.requireUserId(authentication);
        return privateMessageService.listLetterItems(userId, conversationId, normalizePage(page), normalizeSize(size));
    }

    private int normalizePage(Integer page) {
        return page == null ? 0 : Math.max(0, page);
    }

    private int normalizeSize(Integer size) {
        return size == null ? 10 : Math.min(50, Math.max(1, size));
    }
}
