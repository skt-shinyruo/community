package com.nowcoder.community.im.core.application;

import com.nowcoder.community.im.common.command.SendPrivateTextCommand;
import com.nowcoder.community.im.common.event.ImEventIds;
import com.nowcoder.community.im.common.event.PrivateMessageCommittedEvent;
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
            outboxEnqueuer.enqueuePrivateCommitted(toCommittedEvent(existing.get(), command));
            return event;
        }
        PrivateMessagePolicyVerifier.requireAllowed(policyVerifier.verify(draft.fromUserId(), draft.toUserId()));

        var result = privateMessageDomainService.persist(draft);
        var message = result.message();
        PrivateMessagePersistedEvent event = toPersistedEvent(message, command);
        if (result.created()) {
            outboxEnqueuer.enqueuePrivatePersisted(event);
        }
        outboxEnqueuer.enqueuePrivateCommitted(toCommittedEvent(message, command));
        return event;
    }

    private PrivateMessagePersistedEvent toPersistedEvent(PrivateMessageRecord message, SendPrivateTextCommand command) {
        return new PrivateMessagePersistedEvent(
                ImEventIds.privateMessageFact(message.messageId()),
                message.conversationId(),
                message.seq(),
                message.messageId(),
                message.fromUserId(),
                message.toUserId(),
                message.content(),
                message.createdAt().toEpochMilli()
        );
    }

    private PrivateMessageCommittedEvent toCommittedEvent(PrivateMessageRecord message, SendPrivateTextCommand command) {
        return new PrivateMessageCommittedEvent(
                ImEventIds.privateSendResult(command.requestId(), command.clientMsgId(), command.fromUserId()),
                command.requestId(),
                command.clientMsgId(),
                message.fromUserId(),
                message.toUserId(),
                message.conversationId(),
                message.messageId(),
                message.seq(),
                message.createdAt().toEpochMilli()
        );
    }
}
