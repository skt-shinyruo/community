package com.nowcoder.community.content.app.moderation;

import com.nowcoder.community.common.id.UuidV7Generator;
import com.nowcoder.community.content.entity.ModerationAction;
import com.nowcoder.community.content.mapper.ModerationActionMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.UUID;

@Component
public class ModerationAuditWriter {

    private final ModerationActionMapper moderationActionMapper;
    private final UuidV7Generator idGenerator;

    @Autowired
    public ModerationAuditWriter(ModerationActionMapper moderationActionMapper) {
        this(moderationActionMapper, new UuidV7Generator());
    }

    ModerationAuditWriter(ModerationActionMapper moderationActionMapper, UuidV7Generator idGenerator) {
        this.moderationActionMapper = moderationActionMapper;
        this.idGenerator = idGenerator;
    }

    public ModerationAction writeAction(UUID actorId, UUID reportId, String action, String reason, Integer durationSeconds) {
        ModerationAction row = new ModerationAction();
        row.setId(idGenerator.next());
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
