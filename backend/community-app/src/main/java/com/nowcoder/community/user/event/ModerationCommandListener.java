package com.nowcoder.community.user.event;

import com.nowcoder.community.content.contracts.event.ContentContractEvent;
import com.nowcoder.community.content.contracts.event.ContentEventTypes;
import com.nowcoder.community.content.contracts.event.ModerationCommandPayload;
import com.nowcoder.community.user.service.UserModerationApplicationService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class ModerationCommandListener {

    private final UserModerationApplicationService userModerationApplicationService;

    public ModerationCommandListener(UserModerationApplicationService userModerationApplicationService) {
        this.userModerationApplicationService = userModerationApplicationService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = false)
    public void onContentEvent(ContentContractEvent event) {
        if (event == null
                || !ContentEventTypes.MODERATION_COMMAND_REQUESTED.equals(event.type())
                || !(event.payload() instanceof ModerationCommandPayload payload)
                || payload.getUserId() == null) {
            return;
        }
        String action = payload.getAction() == null ? "" : payload.getAction().trim();
        int durationSeconds = payload.getDurationSeconds() == null ? 0 : payload.getDurationSeconds();
        userModerationApplicationService.applyModeration(payload.getUserId(), action, durationSeconds);
    }
}
