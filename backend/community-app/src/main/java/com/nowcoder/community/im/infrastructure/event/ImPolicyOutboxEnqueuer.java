package com.nowcoder.community.im.infrastructure.event;

import com.nowcoder.community.user.contracts.event.UserContractEvent;
import com.nowcoder.community.user.contracts.event.UserEventTypes;
import com.nowcoder.community.user.contracts.event.UserPolicyChangedPayload;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@ConditionalOnProperty(prefix = "events.outbox", name = "enabled", havingValue = "true")
public class ImPolicyOutboxEnqueuer {

    private final ImPolicyChangePublisher imPolicyChangePublisher;

    public ImPolicyOutboxEnqueuer(ImPolicyChangePublisher imPolicyChangePublisher) {
        this.imPolicyChangePublisher = imPolicyChangePublisher;
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT, fallbackExecution = false)
    public void onUserEvent(UserContractEvent event) {
        if (event == null
                || !UserEventTypes.USER_POLICY_CHANGED.equals(event.type())
                || !(event.payload() instanceof UserPolicyChangedPayload payload)
                || payload.getUserId() == null) {
            return;
        }
        imPolicyChangePublisher.publishUserPolicyChanged(payload);
    }
}
