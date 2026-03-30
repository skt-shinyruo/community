package com.nowcoder.community.message.app;

import com.nowcoder.community.infra.idempotency.IdempotencyGuard;
import com.nowcoder.community.infra.security.auth.CurrentUser;
import com.nowcoder.community.message.dto.SendMessageRequest;
import com.nowcoder.community.message.service.PrivateMessageService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
public class SendPrivateMessageUseCase {

    private final PrivateMessageService privateMessageService;
    private final MessageRecipientResolver messageRecipientResolver;
    private final IdempotencyGuard idempotencyGuard;

    public SendPrivateMessageUseCase(
            PrivateMessageService privateMessageService,
            MessageRecipientResolver messageRecipientResolver,
            IdempotencyGuard idempotencyGuard
    ) {
        this.privateMessageService = privateMessageService;
        this.messageRecipientResolver = messageRecipientResolver;
        this.idempotencyGuard = idempotencyGuard;
    }

    public void send(Authentication authentication, String idempotencyKey, SendMessageRequest request) {
        int fromId = CurrentUser.requireUserId(authentication);
        idempotencyGuard.executeRequired("message:send_message", fromId, idempotencyKey, Void.class, () -> {
            int toId = messageRecipientResolver.resolveToUserId(request);
            privateMessageService.send(fromId, toId, request.getContent());
            return null;
        });
    }
}
