package com.nowcoder.community.content.app.moderation;

import com.nowcoder.community.content.event.ContentEventPublisher;
import com.nowcoder.community.content.contracts.event.ModerationCommandPayload;
import org.springframework.stereotype.Component;

@Component
public class UserModerationCommandPublisher {

    private final ContentEventPublisher contentEventPublisher;

    public UserModerationCommandPublisher(ContentEventPublisher contentEventPublisher) {
        this.contentEventPublisher = contentEventPublisher;
    }

    public void publishModerationCommand(
            int actorUserId,
            int reportId,
            int targetUserId,
            String action,
            int durationSeconds,
            String reason
    ) {
        if (targetUserId <= 0) {
            return;
        }
        ModerationCommandPayload payload = new ModerationCommandPayload();
        payload.setUserId(targetUserId);
        payload.setAction(action);
        payload.setDurationSeconds(Math.max(0, durationSeconds));
        payload.setActorUserId(actorUserId <= 0 ? null : actorUserId);
        payload.setReportId(reportId <= 0 ? null : reportId);
        payload.setReason(reason);
        contentEventPublisher.publishModerationCommandRequested(payload);
    }
}
