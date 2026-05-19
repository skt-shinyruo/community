package com.nowcoder.community.im.core.application;

import com.nowcoder.community.im.common.command.SendPrivateTextCommand;
import com.nowcoder.community.im.common.event.PrivateMessagePersistedEvent;
import com.nowcoder.community.im.core.domain.model.PrivateMessageRecord;
import com.nowcoder.community.im.core.domain.service.PrivateMessageDomainService;
import com.nowcoder.community.im.core.outbox.ImMessageOutboxEnqueuer;
import com.nowcoder.community.im.core.policy.PrivateMessagePolicyVerifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PrivateMessageApplicationService {

    private final PrivateMessageDomainService privateMessageDomainService;
    private final PrivateMessagePolicyVerifier policyVerifier;
    private final ImMessageOutboxEnqueuer outboxEnqueuer;

    public PrivateMessageApplicationService(
            PrivateMessageDomainService privateMessageDomainService,
            PrivateMessagePolicyVerifier policyVerifier,
            ImMessageOutboxEnqueuer outboxEnqueuer
    ) {
        this.privateMessageDomainService = privateMessageDomainService;
        this.policyVerifier = policyVerifier;
        this.outboxEnqueuer = outboxEnqueuer;
    }

    @Transactional
    public PrivateMessagePersistedEvent persist(SendPrivateTextCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("command required");
        }
        PrivateMessageDomainService.PrivateMessageDraft draft = privateMessageDomainService.prepare(
                command.fromUserId(),
                command.toUserId(),
                command.conversationId(),
                command.content(),
                command.clientMsgId()
        );
        var existing = privateMessageDomainService.findExisting(draft);
        if (existing.isPresent()) {
            PrivateMessagePersistedEvent event = toPersistedEvent(existing.get(), command);
            outboxEnqueuer.enqueuePrivatePersisted(event);
            return event;
        }
        PrivateMessagePolicyVerifier.requireAllowed(policyVerifier.verify(draft.fromUserId(), draft.toUserId()));

        var message = privateMessageDomainService.persist(draft);
        PrivateMessagePersistedEvent event = toPersistedEvent(message, command);
        outboxEnqueuer.enqueuePrivatePersisted(event);
        return event;
    }

    private PrivateMessagePersistedEvent toPersistedEvent(PrivateMessageRecord message, SendPrivateTextCommand command) {
        return new PrivateMessagePersistedEvent(
                "evt_" + message.messageId(),
                message.conversationId(),
                message.seq(),
                message.messageId(),
                message.fromUserId(),
                message.toUserId(),
                message.content(),
                command.requestId(),
                command.clientMsgId(),
                message.createdAt().toEpochMilli()
        );
    }
}
