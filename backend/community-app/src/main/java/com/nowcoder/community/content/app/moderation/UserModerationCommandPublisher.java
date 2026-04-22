package com.nowcoder.community.content.app.moderation;

import com.nowcoder.community.content.event.ContentEventPublisher;
import com.nowcoder.community.content.contracts.event.ModerationCommandPayload;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class UserModerationCommandPublisher {

    private final ContentEventPublisher contentEventPublisher;

    public UserModerationCommandPublisher(ContentEventPublisher contentEventPublisher) {
        this.contentEventPublisher = contentEventPublisher;
    }

    public void publishModerationCommand(
            UUID actorUserId,
            UUID reportId,
            UUID targetUserId,
            String action,
            int durationSeconds,
            String reason
    ) {
        if (targetUserId == null) {
            return;
        }
        ModerationCommandPayload payload = new ModerationCommandPayload();
        payload.setUserId(targetUserId);
        payload.setAction(action);
        payload.setDurationSeconds(Math.max(0, durationSeconds));
        payload.setActorUserId(actorUserId);
        payload.setReportId(reportId);
        payload.setReason(reason);
        contentEventPublisher.publishModerationCommandRequested(payload);
    }
}
