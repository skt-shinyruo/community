package com.nowcoder.community.content.infrastructure.moderation;

import com.nowcoder.community.content.application.ModerationNoticePublisher;
import com.nowcoder.community.content.contracts.event.ModerationPayload;
import com.nowcoder.community.content.domain.model.ModerationActionRecord;
import com.nowcoder.community.content.domain.model.ModerationTarget;
import com.nowcoder.community.content.domain.model.ReportSnapshot;
import com.nowcoder.community.content.infrastructure.event.ContentEventPublisher;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
public class ContentEventModerationNoticeAdapter implements ModerationNoticePublisher {

    private final ContentEventPublisher contentEventPublisher;

    public ContentEventModerationNoticeAdapter(ContentEventPublisher contentEventPublisher) {
        this.contentEventPublisher = contentEventPublisher;
    }

    @Override
    public void publish(ReportSnapshot report, ModerationActionRecord action, ModerationTarget target, String kind, UUID toUserId) {
        if (toUserId == null) {
            return;
        }
        ModerationPayload payload = new ModerationPayload();
        payload.setReportId(report == null ? null : report.id());
        payload.setKind(kind);
        payload.setToUserId(toUserId);
        payload.setActorUserId(action == null ? null : action.actorId());
        payload.setTargetType(target == null ? null : target.targetType());
        payload.setTargetId(target == null ? null : target.targetId());
        payload.setAction(action == null ? null : action.action());
        payload.setReason(action == null ? null : action.reason());
        payload.setDurationSeconds(action == null ? null : action.durationSeconds());
        payload.setCreateTime(Instant.now());
        contentEventPublisher.publishModerationActionApplied(payload);
    }
}
