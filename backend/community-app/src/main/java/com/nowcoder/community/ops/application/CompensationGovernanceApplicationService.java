package com.nowcoder.community.ops.application;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.exception.CommonErrorCode;
import com.nowcoder.community.ops.application.command.RecordGovernanceAuditCommand;
import com.nowcoder.community.ops.application.result.OutboxLeaseRecoveryResult;
import com.nowcoder.community.ops.domain.model.GovernanceAction;
import com.nowcoder.community.ops.domain.model.GovernanceResult;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
public class CompensationGovernanceApplicationService {

    private static final String OUTBOX_RECOVER_EXPIRED_LEASES = "outboxRecoverExpiredLeases";
    private static final Set<String> ALLOWED_JOBS = Set.of(
            OUTBOX_RECOVER_EXPIRED_LEASES,
            "searchPostProjectionRepair",
            "hotFeedProjectionRepair",
            "growthTaskProjectionRepair",
            "noticeProjectionRepair"
    );

    private final OutboxLeaseRecoveryPort outboxLeaseRecoveryPort;
    private final GovernanceMetrics governanceMetrics;
    private final GovernanceAuditPort governanceAuditPort;

    public CompensationGovernanceApplicationService(
            OutboxLeaseRecoveryPort outboxLeaseRecoveryPort,
            GovernanceMetrics governanceMetrics,
            GovernanceAuditPort governanceAuditPort
    ) {
        this.outboxLeaseRecoveryPort = Objects.requireNonNull(outboxLeaseRecoveryPort, "outboxLeaseRecoveryPort must not be null");
        this.governanceMetrics = Objects.requireNonNull(governanceMetrics, "governanceMetrics must not be null");
        this.governanceAuditPort = Objects.requireNonNull(governanceAuditPort, "governanceAuditPort must not be null");
    }

    public TriggerResult trigger(TriggerCommand command) {
        TriggerCommand c = validate(command);
        TriggerResult result;
        try {
            result = run(c);
        } catch (RuntimeException ex) {
            result = new TriggerResult(
                    c.jobName(),
                    false,
                    0,
                    0,
                    0,
                    GovernanceResult.FAILED.name(),
                    ex.getMessage()
            );
        }
        record(c, result);
        return result;
    }

    private TriggerCommand validate(TriggerCommand command) {
        if (command == null || command.actorUserId() == null) {
            throw new BusinessException(CommonErrorCode.INVALID_ARGUMENT, "actorUserId is required");
        }
        TriggerCommand c = command.normalized();
        if (!ALLOWED_JOBS.contains(c.jobName())) {
            throw new BusinessException(CommonErrorCode.INVALID_ARGUMENT, "compensation job is not allow-listed");
        }
        if (c.limit() < 1 || c.limit() > 500) {
            throw new BusinessException(CommonErrorCode.INVALID_ARGUMENT, "limit must be between 1 and 500");
        }
        if (c.reason().isBlank()) {
            throw new BusinessException(CommonErrorCode.INVALID_ARGUMENT, "compensation reason is required");
        }
        return c;
    }

    private TriggerResult run(TriggerCommand command) {
        if (!OUTBOX_RECOVER_EXPIRED_LEASES.equals(command.jobName())) {
            return new TriggerResult(
                    command.jobName(),
                    false,
                    0,
                    0,
                    command.limit(),
                    GovernanceResult.SKIPPED.name(),
                    "owner repair trigger is not configured for job=" + command.jobName()
            );
        }
        OutboxLeaseRecoveryResult recovery = outboxLeaseRecoveryPort.recoverExpiredLeases(command.limit());
        int skipped = Math.max(0, recovery.selectedCount() - recovery.recoveredCount());
        boolean accepted = recovery.recoveredCount() > 0;
        return new TriggerResult(
                command.jobName(),
                accepted,
                recovery.selectedCount(),
                recovery.recoveredCount(),
                skipped,
                accepted ? GovernanceResult.ACCEPTED.name() : GovernanceResult.SKIPPED.name(),
                "expired outbox leases recovered"
        );
    }

    private void record(TriggerCommand command, TriggerResult result) {
        String resultValue = result.result() == null || result.result().isBlank()
                ? GovernanceResult.FAILED.name()
                : result.result();
        governanceMetrics.recordCompensationTrigger(command.jobName(), resultValue);
        governanceMetrics.recordGovernanceAction(GovernanceAction.COMPENSATION_TRIGGER.name(), resultValue);
        governanceAuditPort.record(new RecordGovernanceAuditCommand(
                GovernanceAction.COMPENSATION_TRIGGER.name(),
                command.actorUserId(),
                "compensation_job",
                command.jobName(),
                "job=" + command.jobName(),
                command.reason(),
                "{\"limit\":" + command.limit() + "}",
                resultValue,
                "{\"accepted\":" + result.accepted()
                        + ",\"processed\":" + result.processedCount()
                        + ",\"repaired\":" + result.repairedCount()
                        + ",\"skipped\":" + result.skippedCount()
                        + ",\"message\":\"" + safeJson(result.message()) + "\"}",
                null
        ));
    }

    private static String safeJson(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    public record TriggerCommand(UUID actorUserId, String jobName, int limit, String reason) {

        TriggerCommand normalized() {
            return new TriggerCommand(actorUserId, trim(jobName), limit, trim(reason));
        }

        private static String trim(String value) {
            return value == null || value.isBlank() ? "" : value.trim();
        }
    }

    public record TriggerResult(
            String jobName,
            boolean accepted,
            int processedCount,
            int repairedCount,
            int skippedCount,
            String result,
            String message
    ) {
    }
}
