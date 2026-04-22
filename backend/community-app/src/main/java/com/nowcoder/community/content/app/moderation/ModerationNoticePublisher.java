package com.nowcoder.community.content.app.moderation;

import com.nowcoder.community.content.entity.ModerationAction;
import com.nowcoder.community.content.entity.Report;
import com.nowcoder.community.content.event.ContentEventPublisher;
import com.nowcoder.community.content.contracts.event.ModerationPayload;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
public class ModerationNoticePublisher {

    private final ContentEventPublisher contentEventPublisher;

    public ModerationNoticePublisher(ContentEventPublisher contentEventPublisher) {
        this.contentEventPublisher = contentEventPublisher;
    }

    public void publish(
            Report report,
            ModerationAction action,
            ModerationTargetResolver.ResolvedTarget target,
            String kind,
            UUID toUserId
    ) {
        if (toUserId == null) {
            return;
        }
        ModerationPayload payload = new ModerationPayload();
        payload.setReportId(report == null ? null : report.getId());
        payload.setKind(kind);
        payload.setToUserId(toUserId);
        payload.setActorUserId(action == null ? null : action.getActorId());
        payload.setTargetType(target == null ? null : target.targetType());
        payload.setTargetId(target == null ? null : target.targetId());
        payload.setAction(action == null ? null : action.getAction());
        payload.setReason(action == null ? null : action.getReason());
        payload.setDurationSeconds(action == null ? null : action.getDurationSeconds());
        payload.setCreateTime(Instant.now());
        contentEventPublisher.publishModerationActionApplied(payload);
    }
}
