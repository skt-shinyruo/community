package com.nowcoder.community.content.app.moderation;

import com.nowcoder.community.content.entity.ModerationAction;
import com.nowcoder.community.content.mapper.ModerationActionMapper;
import org.springframework.stereotype.Component;

import java.util.Date;

@Component
public class ModerationAuditWriter {

    private final ModerationActionMapper moderationActionMapper;

    public ModerationAuditWriter(ModerationActionMapper moderationActionMapper) {
        this.moderationActionMapper = moderationActionMapper;
    }

    public ModerationAction writeAction(int actorId, int reportId, String action, String reason, Integer durationSeconds) {
        ModerationAction row = new ModerationAction();
        row.setReportId(reportId);
        row.setActorId(actorId);
        row.setAction(action);
        row.setReason(reason);
        row.setDurationSeconds(durationSeconds == null ? 0 : Math.max(0, durationSeconds));
        row.setCreateTime(new Date());
        moderationActionMapper.insertAction(row);
        return row;
    }
}
