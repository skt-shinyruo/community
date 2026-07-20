package com.nowcoder.community.content.infrastructure.persistence;

import com.nowcoder.community.common.id.UuidV7Generator;
import com.nowcoder.community.content.domain.model.ModerationActionRecord;
import com.nowcoder.community.content.domain.model.ModerationActionSummary;
import com.nowcoder.community.content.domain.repository.ModerationActionRepository;
import com.nowcoder.community.content.domain.model.ModerationAction;
import com.nowcoder.community.content.infrastructure.persistence.mapper.ModerationActionMapper;
import com.nowcoder.community.common.pagination.Pagination;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class MyBatisModerationActionRepository implements ModerationActionRepository {

    private static final Logger log = LoggerFactory.getLogger(MyBatisModerationActionRepository.class);

    private final ModerationActionMapper moderationActionMapper;
    private final UuidV7Generator idGenerator;

    @Autowired
    public MyBatisModerationActionRepository(ModerationActionMapper moderationActionMapper) {
        this(moderationActionMapper, new UuidV7Generator());
    }

    MyBatisModerationActionRepository(ModerationActionMapper moderationActionMapper, UuidV7Generator idGenerator) {
        this.moderationActionMapper = moderationActionMapper;
        this.idGenerator = idGenerator;
    }

    @Override
    public ModerationActionRecord writeAction(UUID actorId, UUID reportId, String action, String reason, Integer durationSeconds) {
        ModerationAction row = new ModerationAction();
        row.setId(idGenerator.next());
        row.setReportId(reportId);
        row.setActorId(actorId);
        row.setAction(action);
        row.setReason(reason);
        row.setDurationSeconds(durationSeconds == null ? 0 : Math.max(0, durationSeconds));
        row.setCreateTime(new Date());
        moderationActionMapper.insertAction(row);
        return toRecord(row);
    }

    @Override
    public Optional<ModerationActionRecord> findByReportId(UUID reportId) {
        List<ModerationAction> actions = moderationActionMapper.selectActionsByReportId(reportId);
        if (actions.size() > 1) {
            log.error(
                    "moderation action data inconsistency reportId={} actionCount={}",
                    reportId,
                    actions.size()
            );
            throw new IllegalStateException(
                    "multiple moderation actions for reportId=" + reportId + ", count=" + actions.size()
            );
        }
        return actions.stream().findFirst().map(this::toRecord);
    }

    @Override
    public List<ModerationActionSummary> listActions(UUID actorId, int page, int size) {
        int p = Math.max(0, page);
        int s = Math.min(100, Math.max(1, size));
        return moderationActionMapper.selectActions(actorId, Pagination.safeOffset(p, s), s).stream()
                .map(this::toSummary)
                .toList();
    }

    private ModerationActionRecord toRecord(ModerationAction action) {
        return new ModerationActionRecord(
                action.getId(),
                action.getReportId(),
                action.getActorId(),
                action.getAction(),
                action.getReason(),
                action.getDurationSeconds(),
                action.getCreateTime()
        );
    }

    private ModerationActionSummary toSummary(ModerationAction action) {
        return new ModerationActionSummary(
                action.getId(),
                action.getReportId(),
                action.getActorId(),
                action.getAction(),
                action.getReason(),
                action.getDurationSeconds(),
                action.getCreateTime()
        );
    }
}
