package com.nowcoder.community.user.event;

import com.nowcoder.community.content.contracts.event.ContentContractEvent;
import com.nowcoder.community.content.contracts.event.ContentEventTypes;
import com.nowcoder.community.content.contracts.event.ModerationCommandPayload;
import com.nowcoder.community.user.service.UserModerationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class ModerationCommandListener {

    private final UserModerationService userModerationService;

    @Autowired
    public ModerationCommandListener(UserModerationService userModerationService) {
        this.userModerationService = userModerationService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = false)
    public void onContentEvent(ContentContractEvent event) {
        if (event == null
                || !ContentEventTypes.MODERATION_COMMAND_REQUESTED.equals(event.type())
                || !(event.payload() instanceof ModerationCommandPayload payload)
                || payload.getUserId() == null
                || payload.getUserId() <= 0) {
            return;
        }
        String action = payload.getAction() == null ? "" : payload.getAction().trim();
        int durationSeconds = payload.getDurationSeconds() == null ? 0 : payload.getDurationSeconds();
        userModerationService.applyModeration(payload.getUserId(), action, durationSeconds);
    }
}
