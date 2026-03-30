package com.nowcoder.community.message.app;

import com.nowcoder.community.infra.security.auth.CurrentUser;
import com.nowcoder.community.message.dto.MarkReadRequest;
import com.nowcoder.community.message.service.PrivateMessageService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
public class MarkMessagesReadUseCase {

    private final PrivateMessageService privateMessageService;

    public MarkMessagesReadUseCase(PrivateMessageService privateMessageService) {
        this.privateMessageService = privateMessageService;
    }

    public void markRead(Authentication authentication, MarkReadRequest request) {
        int userId = CurrentUser.requireUserId(authentication);
        privateMessageService.markRead(userId, request == null ? null : request.getIds());
    }
}
