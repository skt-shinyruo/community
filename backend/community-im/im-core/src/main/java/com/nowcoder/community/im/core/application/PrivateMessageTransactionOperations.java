package com.nowcoder.community.im.core.application;

import com.nowcoder.community.im.common.command.SendPrivateTextCommand;
import com.nowcoder.community.im.common.event.ImEventIds;
import com.nowcoder.community.im.common.event.PrivateMessageCommittedEvent;
import com.nowcoder.community.im.common.event.PrivateMessagePersistedEvent;
import com.nowcoder.community.im.core.domain.model.PrivateMessageRecord;
import com.nowcoder.community.im.core.domain.repository.UserInboxRepository;
import com.nowcoder.community.im.core.domain.service.PrivateMessageDomainService;
import com.nowcoder.community.im.core.outbox.ImMessageOutboxEnqueuer;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Component
public class PrivateMessageTransactionOperations {

    private final PrivateMessageDomainService privateMessageDomainService;
    private final ImMessageOutboxEnqueuer outboxEnqueuer;
    private final UserInboxRepository userInboxRepository;

    public PrivateMessageTransactionOperations(
            PrivateMessageDomainService privateMessageDomainService,
            ImMessageOutboxEnqueuer outboxEnqueuer,
            UserInboxRepository userInboxRepository
    ) {
        this.privateMessageDomainService = privateMessageDomainService;
        this.outboxEnqueuer = outboxEnqueuer;
        this.userInboxRepository = userInboxRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public Optional<PrivateMessageRecord> findExisting(
            PrivateMessageDomainService.PrivateMessageDraft draft
    ) {
        return privateMessageDomainService.findExisting(draft);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PrivateMessagePersistedEvent persist(
            PrivateMessageDomainService.PrivateMessageDraft draft,
            SendPrivateTextCommand command
    ) {
        PrivateMessageDomainService.PersistResult result = privateMessageDomainService.persist(draft);
        PrivateMessageRecord message = result.message();
        PrivateMessagePersistedEvent event = toPersistedEvent(message);

        userInboxRepository.applyPrivateMessage(message);
        if (result.created()) {
            outboxEnqueuer.enqueuePrivatePersisted(event);
        }
        outboxEnqueuer.enqueuePrivateCommitted(toCommittedEvent(message, command));
        return event;
    }

    private PrivateMessagePersistedEvent toPersistedEvent(PrivateMessageRecord message) {
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

    private PrivateMessageCommittedEvent toCommittedEvent(
            PrivateMessageRecord message,
            SendPrivateTextCommand command
    ) {
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
