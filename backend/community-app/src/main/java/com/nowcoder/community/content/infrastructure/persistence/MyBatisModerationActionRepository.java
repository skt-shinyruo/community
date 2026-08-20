package com.nowcoder.community.content.infrastructure.persistence;

import com.nowcoder.community.common.id.UuidV7Generator;
import com.nowcoder.community.content.domain.model.ModerationActionRecord;
import com.nowcoder.community.content.domain.repository.ModerationActionRepository;
import com.nowcoder.community.content.domain.model.ModerationAction;
import com.nowcoder.community.content.infrastructure.persistence.mapper.ModerationActionMapper;
import com.nowcoder.community.common.pagination.Pagination;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.time.Clock;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Repository
public class MyBatisModerationActionRepository implements ModerationActionRepository {

    private static final Logger log = LoggerFactory.getLogger(MyBatisModerationActionRepository.class);

    private final ModerationActionMapper moderationActionMapper;
    private final UuidV7Generator idGenerator;
    private final Clock clock;

    public MyBatisModerationActionRepository(
            ModerationActionMapper moderationActionMapper,
            UuidV7Generator idGenerator,
            Clock clock
    ) {
        this.moderationActionMapper = Objects.requireNonNull(
                moderationActionMapper, "moderationActionMapper must not be null");
        this.idGenerator = Objects.requireNonNull(idGenerator, "idGenerator must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
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
        row.setCreateTime(Date.from(clock.instant()));
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
    public List<ModerationActionRecord> listActions(UUID actorId, int page, int size) {
        int p = Math.max(0, page);
        int s = Math.min(100, Math.max(1, size));
        return moderationActionMapper.selectActions(actorId, Pagination.safeOffset(p, s), s).stream()
                .map(this::toRecord)
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

}
