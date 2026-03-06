package com.nowcoder.community.user.event;

import com.nowcoder.community.content.api.event.ContentEventTypes;
import com.nowcoder.community.content.api.event.payload.ModerationCommandPayload;
import com.nowcoder.community.content.event.ContentLocalEvent;
import com.nowcoder.community.user.service.InternalUserService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class ModerationCommandListener {

    private final InternalUserService internalUserService;

    public ModerationCommandListener(InternalUserService internalUserService) {
        this.internalUserService = internalUserService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = false)
    public void onContentEvent(ContentLocalEvent event) {
        if (event == null
                || !ContentEventTypes.MODERATION_COMMAND_REQUESTED.equals(event.type())
                || !(event.payload() instanceof ModerationCommandPayload payload)
                || payload.getUserId() == null
                || payload.getUserId() <= 0) {
            return;
        }
        String action = payload.getAction() == null ? "" : payload.getAction().trim();
        int durationSeconds = payload.getDurationSeconds() == null ? 0 : payload.getDurationSeconds();
        internalUserService.applyModeration(payload.getUserId(), action, durationSeconds);
    }
}
