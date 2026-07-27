package com.nowcoder.community.im.core.application;

import com.nowcoder.community.im.common.command.SendPrivateTextCommand;
import com.nowcoder.community.im.common.event.PrivateMessagePersistedEvent;
import com.nowcoder.community.im.core.domain.service.PrivateMessageDomainService;
import com.nowcoder.community.im.core.policy.PrivateMessagePolicyVerifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PrivateMessageApplicationService {

    private final PrivateMessageDomainService privateMessageDomainService;
    private final PrivateMessagePolicyVerifier policyVerifier;
    private final PrivateMessageTransactionOperations transactionOperations;

    public PrivateMessageApplicationService(
            PrivateMessageDomainService privateMessageDomainService,
            PrivateMessagePolicyVerifier policyVerifier,
            PrivateMessageTransactionOperations transactionOperations
    ) {
        this.privateMessageDomainService = privateMessageDomainService;
        this.policyVerifier = policyVerifier;
        this.transactionOperations = transactionOperations;
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
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
        var existing = transactionOperations.findExisting(draft);
        if (existing.isPresent()) {
            return transactionOperations.persist(draft, command);
        }

        PrivateMessagePolicyVerifier.requireAllowed(policyVerifier.verify(draft.fromUserId(), draft.toUserId()));
        return transactionOperations.persist(draft, command);
    }
}
