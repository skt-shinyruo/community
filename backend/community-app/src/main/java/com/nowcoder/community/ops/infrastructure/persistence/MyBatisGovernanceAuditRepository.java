package com.nowcoder.community.ops.infrastructure.persistence;

import com.nowcoder.community.common.id.UuidV7Generator;
import com.nowcoder.community.ops.application.GovernanceAuditPort;
import com.nowcoder.community.ops.application.command.RecordGovernanceAuditCommand;
import com.nowcoder.community.ops.infrastructure.persistence.dataobject.GovernanceAuditDataObject;
import com.nowcoder.community.ops.infrastructure.persistence.mapper.GovernanceAuditMapper;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Repository
public class MyBatisGovernanceAuditRepository implements GovernanceAuditPort {

    private final GovernanceAuditMapper mapper;
    private final UuidV7Generator idGenerator;
    private final Clock clock;

    public MyBatisGovernanceAuditRepository(
            GovernanceAuditMapper mapper,
            UuidV7Generator idGenerator,
            Clock clock
    ) {
        this.mapper = Objects.requireNonNull(mapper, "mapper must not be null");
        this.idGenerator = Objects.requireNonNull(idGenerator, "idGenerator must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    public void record(RecordGovernanceAuditCommand command) {
        RecordGovernanceAuditCommand c = Objects.requireNonNull(command, "command must not be null").normalized();
        if (!StringUtils.hasText(c.action()) || c.actorUserId() == null || !StringUtils.hasText(c.targetType())
                || !StringUtils.hasText(c.result())) {
            throw new IllegalArgumentException("action, actorUserId, targetType and result are required");
        }
        UUID id = idGenerator.next();
        Instant now = clock.instant();
        GovernanceAuditDataObject row = new GovernanceAuditDataObject();
        row.setId(id);
        row.setAction(c.action());
        row.setActorUserId(c.actorUserId());
        row.setTargetType(c.targetType());
        row.setTargetId(c.targetId());
        row.setScope(c.scope());
        row.setReason(truncate(c.reason(), 512));
        row.setRequestJson(c.requestJson());
        row.setResult(c.result());
        row.setSummaryJson(c.summaryJson());
        row.setTraceId(truncate(c.traceId(), 32));
        row.setCreatedAt(now);

        mapper.insert(row);
    }

    private static String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
