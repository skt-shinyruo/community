package com.nowcoder.community.ops.application.command;

import org.springframework.util.StringUtils;

import java.util.UUID;

public record RecordGovernanceAuditCommand(
        String action,
        UUID actorUserId,
        String targetType,
        String targetId,
        String scope,
        String reason,
        String requestJson,
        String result,
        String summaryJson,
        String traceId
) {

    public RecordGovernanceAuditCommand normalized() {
        return new RecordGovernanceAuditCommand(
                trim(action),
                actorUserId,
                trim(targetType),
                trim(targetId),
                trim(scope),
                trim(reason),
                trim(requestJson),
                trim(result),
                trim(summaryJson),
                trim(traceId)
        );
    }

    private static String trim(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
